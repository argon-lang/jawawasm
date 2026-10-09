use super::*;

#[derive(Debug)]
enum Event {
    Error(String),
    Module(String, Vec<u8>),
    Json(serde_json::Value),
}

pub unsafe extern "C" fn jawawasm_report_error(ctx: *mut c_void, message: *const u8, len: usize) {
    let events = unsafe { &mut *ctx.cast::<Vec<Event>>() };
    let message = unsafe { slice::from_raw_parts(message, len) };
    events.push(Event::Error(str::from_utf8(message).unwrap().to_owned()));
}

pub unsafe extern "C" fn jawawasm_report_module(
    ctx: *mut c_void,
    id: *const u8,
    id_len: usize,
    buff: *const u8,
    buff_len: usize,
) {
    let events = unsafe { &mut *ctx.cast::<Vec<Event>>() };
    let id = unsafe { slice::from_raw_parts(id, id_len) };
    let bytes = unsafe { slice::from_raw_parts(buff, buff_len) };
    events.push(Event::Module(
        str::from_utf8(id).unwrap().to_owned(),
        bytes.to_vec(),
    ));
}

pub unsafe extern "C" fn jawawasm_report_json(ctx: *mut c_void, buff: *const u8, len: usize) {
    let events = unsafe { &mut *ctx.cast::<Vec<Event>>() };
    let bytes = unsafe { slice::from_raw_parts(buff, len) };
    events.push(Event::Json(serde_json::from_slice(bytes).unwrap()));
}

type ParseFn = unsafe extern "C" fn(*mut c_void, *const u8, usize);

fn run(parse: ParseFn, source: &[u8]) -> Vec<Event> {
    let mut events = Vec::<Event>::new();
    unsafe {
        parse(
            ptr::from_mut(&mut events).cast(),
            source.as_ptr(),
            source.len(),
        );
    }
    events
}

#[test]
fn allocation_round_trip() {
    let len = 31;
    let buff = jawawasm_allocate(len);
    assert!(!buff.is_null());
    unsafe {
        let bytes = slice::from_raw_parts_mut(buff, len);
        assert!(bytes.iter().all(|byte| *byte == 0));
        bytes.fill(42);
        jawawasm_free(buff, len);
        jawawasm_free(ptr::null_mut(), 0);
    }
    assert!(jawawasm_allocate(0).is_null());
}

#[test]
fn wat_reports_named_and_unnamed_modules() {
    for (source, expected_id) in [("(module $hello)", "hello"), ("(module)", "")] {
        let events = run(jawawasm_parse_wat, source.as_bytes());
        let [Event::Module(id, bytes)] = events.as_slice() else {
            panic!("unexpected events: {events:?}");
        };
        assert_eq!(id, expected_id);
        assert!(bytes.starts_with(b"\0asm\x01\0\0\0"));
    }
}

#[test]
fn allocated_input_can_be_parsed_and_freed() {
    let source = b"(module)";
    let buff = jawawasm_allocate(source.len());
    let mut events = Vec::<Event>::new();
    unsafe {
        ptr::copy_nonoverlapping(source.as_ptr(), buff, source.len());
        jawawasm_parse_wat(ptr::from_mut(&mut events).cast(), buff, source.len());
        jawawasm_free(buff, source.len());
    }
    assert!(matches!(events.as_slice(), [Event::Module(_, _)]));
}

#[test]
fn wast_reports_files_before_json_with_matching_names() {
    let source = br#"
        (module $first (func (export "answer") (result i32) i32.const 42))
        (assert_return (invoke $first "answer") (i32.const 42))
        (module $second)
    "#;
    let events = run(jawawasm_parse_wast, source);
    let [
        Event::Module(first_id, first),
        Event::Module(second_id, second),
        Event::Json(json),
    ] = events.as_slice()
    else {
        panic!("unexpected events: {events:?}");
    };
    assert_eq!(json["source_filename"], "input.wast");
    assert_eq!(json["commands"][0]["filename"], first_id.as_str());
    assert_eq!(json["commands"][0]["name"], "first");
    assert_eq!(json["commands"][1]["type"], "assert_return");
    assert_eq!(json["commands"][1]["expected"][0]["value"], "42");
    assert_eq!(json["commands"][2]["filename"], second_id.as_str());
    assert_ne!(first_id, second_id);
    assert!(first.starts_with(b"\0asm"));
    assert!(second.starts_with(b"\0asm"));
}

#[test]
fn wast_preserves_malformed_quoted_text() {
    let events = run(
        jawawasm_parse_wast,
        br#"(assert_malformed (module quote "(module") "unexpected end")"#,
    );
    let [Event::Module(id, text), Event::Json(json)] = events.as_slice() else {
        panic!("unexpected events: {events:?}");
    };
    assert!(text.starts_with(b"(module"));
    assert_eq!(json["commands"][0]["filename"], id.as_str());
    assert_eq!(json["commands"][0]["module_type"], "text");
    assert_eq!(json["commands"][0]["type"], "assert_malformed");
}

#[test]
fn errors_produce_only_an_error_callback() {
    for parse in [jawawasm_parse_wat as ParseFn, jawawasm_parse_wast] {
        for source in [b"(module".as_slice(), b"\xff"] {
            let events = run(parse, source);
            let [Event::Error(message)] = events.as_slice() else {
                panic!("unexpected events: {events:?}");
            };
            assert!(!message.is_empty());
        }
    }
    // An earlier valid module must not be reported if later conversion fails.
    let events = run(
        jawawasm_parse_wast,
        b"(module) (module (func (call $missing)))",
    );
    assert!(matches!(events.as_slice(), [Event::Error(_)]));
}

#[test]
fn null_input_is_checked() {
    for parse in [jawawasm_parse_wat as ParseFn, jawawasm_parse_wast] {
        let mut events = Vec::<Event>::new();
        unsafe { parse(ptr::from_mut(&mut events).cast(), ptr::null(), 1) };
        assert!(matches!(events.as_slice(), [Event::Error(_)]));
    }
    let mut events = Vec::<Event>::new();
    unsafe { jawawasm_parse_wast(ptr::from_mut(&mut events).cast(), ptr::null(), 0) };
    assert!(matches!(events.as_slice(), [Event::Error(_)]));
}
