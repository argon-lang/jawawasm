//! WebAssembly text parsing with a callback-based C ABI.
//!
//! See the crate README for ownership and callback contracts.

use std::alloc::{Layout, alloc_zeroed, dealloc, handle_alloc_error};
use std::ffi::c_void;
use std::{ptr, slice, str};

use anyhow::{Result, ensure};
use wast::parser::{self, ParseBuffer};

#[cfg(not(test))]
mod host {
    use std::ffi::c_void;

    #[cfg_attr(target_arch = "wasm32", link(wasm_import_module = "callback"))]
    unsafe extern "C" {
        pub fn jawawasm_report_error(ctx: *mut c_void, message: *const u8, len: usize);
        pub fn jawawasm_report_module(
            ctx: *mut c_void,
            id: *const u8,
            id_len: usize,
            buff: *const u8,
            buff_len: usize,
        );
        pub fn jawawasm_report_json(ctx: *mut c_void, buff: *const u8, len: usize);
    }
}

/// Allocates a zero-initialized input buffer, or returns null for zero bytes.
#[unsafe(no_mangle)]
pub extern "C" fn jawawasm_allocate(len: usize) -> *mut u8 {
    if len == 0 {
        return ptr::null_mut();
    }
    let layout = Layout::array::<u8>(len).expect("input buffer is too large");
    // SAFETY: The layout has nonzero size and valid alignment.
    let buff = unsafe { alloc_zeroed(layout) };
    if buff.is_null() {
        handle_alloc_error(layout);
    }
    buff
}

/// Frees an input buffer. Null is a no-op.
///
/// # Safety
/// A non-null pointer must come from `jawawasm_allocate(len)` with the same
/// length, must not have been freed, and must no longer be in use.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn jawawasm_free(buff: *mut u8, len: usize) {
    if !buff.is_null() {
        let layout = Layout::array::<u8>(len).expect("input buffer is too large");
        // SAFETY: The caller supplies the original allocation and matching layout.
        unsafe { dealloc(buff, layout) };
    }
}

/// Parses WAT and reports its binary encoding through the host callback.
///
/// # Safety
/// `buff` must identify `len` readable bytes (or be null for zero bytes),
/// remaining valid and unchanged until this function returns. Host callbacks
/// must obey the README's buffer and unwinding contracts.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn jawawasm_parse_wat(ctx: *mut c_void, buff: *const u8, len: usize) {
    // SAFETY: The input and callback contracts are delegated to the caller.
    let result = unsafe { input(buff, len) }.and_then(parse_wat);
    match result {
        Ok((id, bytes)) => unsafe { report_module(ctx, &id, &bytes) },
        Err(error) => unsafe { report_error(ctx, &format!("{error:#}")) },
    }
}

/// Parses WAST and reports generated files followed by the script JSON.
///
/// # Safety
/// Same input and callback requirements as `jawawasm_parse_wat`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn jawawasm_parse_wast(ctx: *mut c_void, buff: *const u8, len: usize) {
    // Complete conversion and serialization before reporting any output.
    let result = unsafe { input(buff, len) }.and_then(parse_wast);
    match result {
        Ok((modules, json)) => {
            for (id, bytes) in modules {
                unsafe { report_module(ctx, &id, &bytes) };
            }
            unsafe { host::jawawasm_report_json(ctx, json.as_ptr(), json.len()) };
        }
        Err(error) => unsafe { report_error(ctx, &format!("{error:#}")) },
    }
}

unsafe fn input<'a>(buff: *const u8, len: usize) -> Result<&'a str> {
    if len == 0 {
        return Ok("");
    }
    ensure!(!buff.is_null(), "input buffer is null with nonzero length");
    ensure!(len <= isize::MAX as usize, "input buffer is too large");
    // SAFETY: Non-null and size checked above; readable memory is the caller's contract.
    Ok(str::from_utf8(unsafe { slice::from_raw_parts(buff, len) })?)
}

fn parse_wat(source: &str) -> Result<(String, Vec<u8>)> {
    let buffer = ParseBuffer::new(source).map_err(|error| diagnostic(error, source))?;
    let mut wat: wast::Wat<'_> =
        parser::parse(&buffer).map_err(|error| diagnostic(error, source))?;
    let id = match &wat {
        wast::Wat::Module(module) => module.id,
        wast::Wat::Component(component) => component.id,
    }
    .map(|id| id.name().to_owned())
    .unwrap_or_default();
    let bytes = wat.encode().map_err(|error| diagnostic(error, source))?;
    Ok((id, bytes))
}

type ModuleFiles = Vec<(String, Vec<u8>)>;

fn parse_wast(source: &str) -> Result<(ModuleFiles, Vec<u8>)> {
    // Match wasm-tools json-from-wast, including the specification's names tests.
    let mut lexer = wast::lexer::Lexer::new(source);
    lexer.allow_confusing_unicode(true);
    let buffer = ParseBuffer::new_with_lexer(lexer).map_err(|error| diagnostic(error, source))?;
    let ast = parser::parse(&buffer).map_err(|error| diagnostic(error, source))?;
    let script = json_from_wast::Wast::from_ast("input.wast", source, ast)?;
    let json = serde_json::to_vec(&script)?;
    Ok((script.wasms, json))
}

fn diagnostic(mut error: wast::Error, source: &str) -> wast::Error {
    error.set_text(source);
    error
}

unsafe fn report_module(ctx: *mut c_void, id: &str, bytes: &[u8]) {
    // SAFETY: Borrowed buffers remain live for the duration of the callback.
    unsafe {
        host::jawawasm_report_module(ctx, id.as_ptr(), id.len(), bytes.as_ptr(), bytes.len())
    };
}

unsafe fn report_error(ctx: *mut c_void, message: &str) {
    // SAFETY: The message remains live for the duration of the callback.
    unsafe { host::jawawasm_report_error(ctx, message.as_ptr(), message.len()) };
}

#[cfg(test)]
#[path = "tests.rs"]
mod host;
