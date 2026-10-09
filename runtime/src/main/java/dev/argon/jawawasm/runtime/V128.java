package dev.argon.jawawasm.runtime;

/**
 * A 128-bit vector value stored as two 64-bit halves. Lanes are ordered
 * little-endian within each half.
 * 
 * @param low
 *            Bits 0 through 63.
 * @param high
 *            Bits 64 through 127.
 */
public record V128(long low, long high) {
	/**
	 * A vector of zeroes.
	 */
	public static final V128 ZERO = new V128(0, 0);

	/**
	 * Create a vector from an integer to fill the low lane, with the 3 higher lanes
	 * filled with zero.
	 * 
	 * @param value
	 *            The low lane value.
	 * @return The vector.
	 */
	public static V128 ofIntZero(int value) {
		return new V128(Integer.toUnsignedLong(value), 0);
	}

	/**
	 *
	 * Create a vector from an integer to fill the low lane, with the high lane
	 * filled with zero.
	 * 
	 * @param value
	 *            The low lane value.
	 * @return The vector.
	 */
	public static V128 ofLongZero(long value) {
		return new V128(value, 0);
	}

	/**
	 * Convert 8x8 to 16x8 by sign extending.
	 * 
	 * @param value
	 *            The 8x8 value.
	 * @return The 16x8 value.
	 */
	public static V128 of8x8S(long value) {
		return new V128(
			Short.toUnsignedLong((byte)value) | Short.toUnsignedLong((byte)(value >> 8)) << 16
				| Short.toUnsignedLong((byte)(value >> 16)) << 32 | Short.toUnsignedLong((byte)(value >> 24)) << 48,
			Short.toUnsignedLong((byte)(value >> 32)) | Short.toUnsignedLong((byte)(value >> 40)) << 16
				| Short.toUnsignedLong((byte)(value >> 48)) << 32 | Short.toUnsignedLong((byte)(value >> 56)) << 48
		);
	}

	/**
	 * Convert 8x8 to 16x8 by zero extending.
	 * 
	 * @param value
	 *            The 8x8 value.
	 * @return The 16x8 value.
	 */
	public static V128 of8x8U(long value) {
		return new V128(
			Short.toUnsignedLong((short)Byte.toUnsignedInt((byte)value))
				| Short.toUnsignedLong((short)Byte.toUnsignedInt((byte)(value >> 8))) << 16
				| Short.toUnsignedLong((short)Byte.toUnsignedInt((byte)(value >> 16))) << 32
				| Short.toUnsignedLong((short)Byte.toUnsignedInt((byte)(value >> 24))) << 48,
			Short.toUnsignedLong((short)Byte.toUnsignedInt((byte)(value >> 32)))
				| Short.toUnsignedLong((short)Byte.toUnsignedInt((byte)(value >> 40))) << 16
				| Short.toUnsignedLong((short)Byte.toUnsignedInt((byte)(value >> 48))) << 32
				| Short.toUnsignedLong((short)Byte.toUnsignedInt((byte)(value >> 56))) << 48
		);
	}

	/**
	 * Convert 16x4 to 32x4 by sign extending.
	 * 
	 * @param value
	 *            The 16x4 value.
	 * @return The 32x4 value.
	 */
	public static V128 of16x4S(long value) {
		return new V128(
			Integer.toUnsignedLong((short)value) | Integer.toUnsignedLong((short)(value >> 16)) << 32,
			Integer.toUnsignedLong((short)(value >> 32)) | Integer.toUnsignedLong((short)(value >> 48)) << 32
		);
	}

	/**
	 * Convert 16x4 to 32x4 by zero extending.
	 * 
	 * @param value
	 *            The 16x4 value.
	 * @return The 32x4 value.
	 */
	public static V128 of16x4U(long value) {
		return new V128(
			Integer.toUnsignedLong(Short.toUnsignedInt((short)value))
				| Integer.toUnsignedLong(Short.toUnsignedInt((short)(value >> 16))) << 32,
			Integer.toUnsignedLong(Short.toUnsignedInt((short)(value >> 32)))
				| Integer.toUnsignedLong(Short.toUnsignedInt((short)(value >> 48))) << 32
		);
	}

	/**
	 * Convert 32x2 to 64x2 by sign extending.
	 * 
	 * @param value
	 *            The 32x2 value.
	 * @return The 64x2 value.
	 */
	public static V128 of32x2S(long value) {
		return new V128((long)(int)value, (long)(int)(value >> 32));
	}

	/**
	 * Convert 32x2 to 64x2 by zero extending.
	 * 
	 * @param value
	 *            The 32x2 value.
	 * @return The 64x2 value.
	 */
	public static V128 of32x2U(long value) {
		return new V128(Integer.toUnsignedLong((int)value), Integer.toUnsignedLong((int)(value >> 32)));
	}

	/**
	 * Get an 8-bit lane value.
	 * 
	 * @param i
	 *            The lane index.
	 * @return The value.
	 */
	public byte extractLane8(int i) {
		if(i < 0 || i >= 16) {
			throw new IllegalArgumentException();
		}
		return (byte)((i < 8 ? low : high) >>> ((i & 7) * 8));
	}

	/**
	 * Get a 16-bit lane value.
	 * 
	 * @param i
	 *            The lane index.
	 * @return The value.
	 */
	public short extractLane16(int i) {
		if(i < 0 || i >= 8) {
			throw new IllegalArgumentException();
		}
		return (short)((i < 4 ? low : high) >>> ((i & 3) * 16));
	}

	/**
	 * Get a 32-bit lane value.
	 * 
	 * @param i
	 *            The lane index.
	 * @return The value.
	 */
	public int extractLane32(int i) {
		if(i < 0 || i >= 4) {
			throw new IllegalArgumentException();
		}
		return (int)((i < 2 ? low : high) >>> ((i & 1) * 32));
	}

	/**
	 * Get a 64-bit lane value.
	 * 
	 * @param i
	 *            The lane index.
	 * @return The value.
	 */
	public long extractLane64(int i) {
		return switch(i) {
			case 0 -> low;
			case 1 -> high;
			default -> throw new IllegalArgumentException();
		};
	}

	/**
	 * Get a 32-bit float lane value.
	 * 
	 * @param i
	 *            The lane index.
	 * @return The value.
	 */
	public float extractLaneF32(int i) {
		if(i < 0 || i >= 4) {
			throw new IllegalArgumentException();
		}
		return Float.intBitsToFloat((int)((i < 2 ? low : high) >>> ((i & 1) * 32)));
	}

	/**
	 * Get a 64-bit float lane value.
	 * 
	 * @param i
	 *            The lane index.
	 * @return The value.
	 */
	public double extractLaneF64(int i) {
		return Double.longBitsToDouble(switch(i) {
			case 0 -> low;
			case 1 -> high;
			default -> throw new IllegalArgumentException();
		});
	}

	/**
	 * Check for true values.
	 * 
	 * @return true iff any of the bits are set
	 */
	public boolean anyTrue() {
		return (low | high) != 0;
	}

	/**
	 * Narrow two vectors with 16-bit lanes to a single vector with 8-bit lanes
	 * using signed operations.
	 * 
	 * @param b
	 *            The second vector
	 * @return The result.
	 */
	public V128 narrow16To8Signed(V128 b) {
		return new V128(
			Byte.toUnsignedLong(Util.narrowS16I8((short)low))
				| Byte.toUnsignedLong(Util.narrowS16I8((short)(low >>> 16))) << 8
				| Byte.toUnsignedLong(Util.narrowS16I8((short)(low >>> 32))) << 16
				| Byte.toUnsignedLong(Util.narrowS16I8((short)(low >>> 48))) << 24
				| Byte.toUnsignedLong(Util.narrowS16I8((short)high)) << 32
				| Byte.toUnsignedLong(Util.narrowS16I8((short)(high >>> 16))) << 40
				| Byte.toUnsignedLong(Util.narrowS16I8((short)(high >>> 32))) << 48
				| Byte.toUnsignedLong(Util.narrowS16I8((short)(high >>> 48))) << 56,
			Byte.toUnsignedLong(Util.narrowS16I8((short)b.low))
				| Byte.toUnsignedLong(Util.narrowS16I8((short)(b.low >>> 16))) << 8
				| Byte.toUnsignedLong(Util.narrowS16I8((short)(b.low >>> 32))) << 16
				| Byte.toUnsignedLong(Util.narrowS16I8((short)(b.low >>> 48))) << 24
				| Byte.toUnsignedLong(Util.narrowS16I8((short)b.high)) << 32
				| Byte.toUnsignedLong(Util.narrowS16I8((short)(b.high >>> 16))) << 40
				| Byte.toUnsignedLong(Util.narrowS16I8((short)(b.high >>> 32))) << 48
				| Byte.toUnsignedLong(Util.narrowS16I8((short)(b.high >>> 48))) << 56
		);
	}

	/**
	 * Narrow two vectors with 16-bit lanes to a single vector with 8-bit lanes
	 * using unsigned operations.
	 * 
	 * @param b
	 *            The second vector
	 * @return The result.
	 */
	public V128 narrow16To8Unsigned(V128 b) {
		return new V128(
			Byte.toUnsignedLong(Util.narrowU16I8((short)low))
				| Byte.toUnsignedLong(Util.narrowU16I8((short)(low >>> 16))) << 8
				| Byte.toUnsignedLong(Util.narrowU16I8((short)(low >>> 32))) << 16
				| Byte.toUnsignedLong(Util.narrowU16I8((short)(low >>> 48))) << 24
				| Byte.toUnsignedLong(Util.narrowU16I8((short)high)) << 32
				| Byte.toUnsignedLong(Util.narrowU16I8((short)(high >>> 16))) << 40
				| Byte.toUnsignedLong(Util.narrowU16I8((short)(high >>> 32))) << 48
				| Byte.toUnsignedLong(Util.narrowU16I8((short)(high >>> 48))) << 56,
			Byte.toUnsignedLong(Util.narrowU16I8((short)b.low))
				| Byte.toUnsignedLong(Util.narrowU16I8((short)(b.low >>> 16))) << 8
				| Byte.toUnsignedLong(Util.narrowU16I8((short)(b.low >>> 32))) << 16
				| Byte.toUnsignedLong(Util.narrowU16I8((short)(b.low >>> 48))) << 24
				| Byte.toUnsignedLong(Util.narrowU16I8((short)b.high)) << 32
				| Byte.toUnsignedLong(Util.narrowU16I8((short)(b.high >>> 16))) << 40
				| Byte.toUnsignedLong(Util.narrowU16I8((short)(b.high >>> 32))) << 48
				| Byte.toUnsignedLong(Util.narrowU16I8((short)(b.high >>> 48))) << 56
		);
	}

	/**
	 * Narrow two vectors with 32-bit lanes to a single vector with 16-bit lanes
	 * using signed operations.
	 * 
	 * @param b
	 *            The second vector
	 * @return The result.
	 */
	public V128 narrow32To16Signed(V128 b) {
		return new V128(
			Short.toUnsignedLong(Util.narrowS32I16((int)low))
				| Short.toUnsignedLong(Util.narrowS32I16((int)(low >>> 32))) << 16
				| Short.toUnsignedLong(Util.narrowS32I16((int)high)) << 32
				| Short.toUnsignedLong(Util.narrowS32I16((int)(high >>> 32))) << 48,
			Short.toUnsignedLong(Util.narrowS32I16((int)b.low))
				| Short.toUnsignedLong(Util.narrowS32I16((int)(b.low >>> 32))) << 16
				| Short.toUnsignedLong(Util.narrowS32I16((int)b.high)) << 32
				| Short.toUnsignedLong(Util.narrowS32I16((int)(b.high >>> 32))) << 48
		);
	}

	/**
	 * Narrow two vectors with 32-bit lanes to a single vector with 16-bit lanes
	 * using unsigned operations.
	 * 
	 * @param b
	 *            The second vector
	 * @return The result.
	 */
	public V128 narrow32To16Unsigned(V128 b) {
		return new V128(
			Short.toUnsignedLong(Util.narrowU32I16((int)low))
				| Short.toUnsignedLong(Util.narrowU32I16((int)(low >>> 32))) << 16
				| Short.toUnsignedLong(Util.narrowU32I16((int)high)) << 32
				| Short.toUnsignedLong(Util.narrowU32I16((int)(high >>> 32))) << 48,
			Short.toUnsignedLong(Util.narrowU32I16((int)b.low))
				| Short.toUnsignedLong(Util.narrowU32I16((int)(b.low >>> 32))) << 16
				| Short.toUnsignedLong(Util.narrowU32I16((int)b.high)) << 32
				| Short.toUnsignedLong(Util.narrowU32I16((int)(b.high >>> 32))) << 48
		);
	}

	/**
	 * Truncate a vector from F32 to U32.
	 * 
	 * @return The result.
	 */
	public V128 truncateSatF32ToU32() {
		return new V128(
			Integer.toUnsignedLong(Util.truncSatF32U32(Float.intBitsToFloat((int)low)))
				| Integer.toUnsignedLong(Util.truncSatF32U32(Float.intBitsToFloat((int)(low >>> 32)))) << 32,
			Integer.toUnsignedLong(Util.truncSatF32U32(Float.intBitsToFloat((int)high)))
				| Integer.toUnsignedLong(Util.truncSatF32U32(Float.intBitsToFloat((int)(high >>> 32)))) << 32
		);
	}

	/**
	 * Truncate a vector from F32 to S32.
	 * 
	 * @return The result.
	 */
	public V128 truncateSatF32ToS32() {
		return new V128(
			Integer.toUnsignedLong((int)Float.intBitsToFloat((int)low))
				| Integer.toUnsignedLong((int)Float.intBitsToFloat((int)(low >>> 32))) << 32,
			Integer.toUnsignedLong((int)Float.intBitsToFloat((int)high))
				| Integer.toUnsignedLong((int)Float.intBitsToFloat((int)(high >>> 32))) << 32
		);
	}

	/**
	 * Truncate a vector from F64 to U32. The high half will be filled with zeroes.
	 * 
	 * @return The result.
	 */
	public V128 truncateSatF64ToU32Zero() {
		return new V128(
			Integer.toUnsignedLong(Util.truncSatF64U32(Double.longBitsToDouble(low)))
				| Integer.toUnsignedLong(Util.truncSatF64U32(Double.longBitsToDouble(high))) << 32,
			Integer.toUnsignedLong(0) | Integer.toUnsignedLong(0) << 32
		);
	}

	/**
	 * Truncate a vector from F64 to S32. The high half will be filled with zeroes.
	 * 
	 * @return The result.
	 */
	public V128 truncateSatF64ToS32Zero() {
		return new V128(
			Integer.toUnsignedLong((int)Double.longBitsToDouble(low))
				| Integer.toUnsignedLong((int)Double.longBitsToDouble(high)) << 32,
			Integer.toUnsignedLong(0) | Integer.toUnsignedLong(0) << 32
		);
	}

	/**
	 * Extend the low half of the vector from 8-bit unsigned to 16-bit
	 * 
	 * @return The result.
	 */
	public V128 extendLowU8To16() {
		return new V128(
			Short.toUnsignedLong((short)Byte.toUnsignedInt((byte)low))
				| Short.toUnsignedLong((short)Byte.toUnsignedInt((byte)(low >>> 8))) << 16
				| Short.toUnsignedLong((short)Byte.toUnsignedInt((byte)(low >>> 16))) << 32
				| Short.toUnsignedLong((short)Byte.toUnsignedInt((byte)(low >>> 24))) << 48,
			Short.toUnsignedLong((short)Byte.toUnsignedInt((byte)(low >>> 32)))
				| Short.toUnsignedLong((short)Byte.toUnsignedInt((byte)(low >>> 40))) << 16
				| Short.toUnsignedLong((short)Byte.toUnsignedInt((byte)(low >>> 48))) << 32
				| Short.toUnsignedLong((short)Byte.toUnsignedInt((byte)(low >>> 56))) << 48
		);
	}

	/**
	 * Extend the low half of the vector from 8-bit signed to 16-bit
	 * 
	 * @return The result.
	 */
	public V128 extendLowS8To16() {
		return new V128(
			Short.toUnsignedLong((byte)low) | Short.toUnsignedLong((byte)(low >>> 8)) << 16
				| Short.toUnsignedLong((byte)(low >>> 16)) << 32 | Short.toUnsignedLong((byte)(low >>> 24)) << 48,
			Short.toUnsignedLong((byte)(low >>> 32)) | Short.toUnsignedLong((byte)(low >>> 40)) << 16
				| Short.toUnsignedLong((byte)(low >>> 48)) << 32 | Short.toUnsignedLong((byte)(low >>> 56)) << 48
		);
	}

	/**
	 * Extend the high half of the vector from 8-bit unsigned to 16-bit
	 * 
	 * @return The result.
	 */
	public V128 extendHighU8To16() {
		return new V128(
			Short.toUnsignedLong((short)Byte.toUnsignedInt((byte)high))
				| Short.toUnsignedLong((short)Byte.toUnsignedInt((byte)(high >>> 8))) << 16
				| Short.toUnsignedLong((short)Byte.toUnsignedInt((byte)(high >>> 16))) << 32
				| Short.toUnsignedLong((short)Byte.toUnsignedInt((byte)(high >>> 24))) << 48,
			Short.toUnsignedLong((short)Byte.toUnsignedInt((byte)(high >>> 32)))
				| Short.toUnsignedLong((short)Byte.toUnsignedInt((byte)(high >>> 40))) << 16
				| Short.toUnsignedLong((short)Byte.toUnsignedInt((byte)(high >>> 48))) << 32
				| Short.toUnsignedLong((short)Byte.toUnsignedInt((byte)(high >>> 56))) << 48
		);
	}

	/**
	 * Extend the high half of the vector from 8-bit signed to 16-bit
	 * 
	 * @return The result.
	 */
	public V128 extendHighS8To16() {
		return new V128(
			Short.toUnsignedLong((byte)high) | Short.toUnsignedLong((byte)(high >>> 8)) << 16
				| Short.toUnsignedLong((byte)(high >>> 16)) << 32 | Short.toUnsignedLong((byte)(high >>> 24)) << 48,
			Short.toUnsignedLong((byte)(high >>> 32)) | Short.toUnsignedLong((byte)(high >>> 40)) << 16
				| Short.toUnsignedLong((byte)(high >>> 48)) << 32 | Short.toUnsignedLong((byte)(high >>> 56)) << 48
		);
	}

	/**
	 * Extend the low half of the vector from 16-bit unsigned to 32-bit
	 * 
	 * @return The result.
	 */
	public V128 extendLowU16To32() {
		return new V128(
			Integer.toUnsignedLong(Short.toUnsignedInt((short)low))
				| Integer.toUnsignedLong(Short.toUnsignedInt((short)(low >>> 16))) << 32,
			Integer.toUnsignedLong(Short.toUnsignedInt((short)(low >>> 32)))
				| Integer.toUnsignedLong(Short.toUnsignedInt((short)(low >>> 48))) << 32
		);
	}

	/**
	 * Extend the low half of the vector from 16-bit signed to 32-bit
	 * 
	 * @return The result.
	 */
	public V128 extendLowS16To32() {
		return new V128(
			Integer.toUnsignedLong((short)low) | Integer.toUnsignedLong((short)(low >>> 16)) << 32,
			Integer.toUnsignedLong((short)(low >>> 32)) | Integer.toUnsignedLong((short)(low >>> 48)) << 32
		);
	}

	/**
	 * Extend the high half of the vector from 16-bit unsigned to 32-bit
	 * 
	 * @return The result.
	 */
	public V128 extendHighU16To32() {
		return new V128(
			Integer.toUnsignedLong(Short.toUnsignedInt((short)high))
				| Integer.toUnsignedLong(Short.toUnsignedInt((short)(high >>> 16))) << 32,
			Integer.toUnsignedLong(Short.toUnsignedInt((short)(high >>> 32)))
				| Integer.toUnsignedLong(Short.toUnsignedInt((short)(high >>> 48))) << 32
		);
	}

	/**
	 * Extend the high half of the vector from 16-bit signed to 32-bit
	 * 
	 * @return The result.
	 */
	public V128 extendHighS16To32() {
		return new V128(
			Integer.toUnsignedLong((short)high) | Integer.toUnsignedLong((short)(high >>> 16)) << 32,
			Integer.toUnsignedLong((short)(high >>> 32)) | Integer.toUnsignedLong((short)(high >>> 48)) << 32
		);
	}

	/**
	 * Extend the low half of the vector from 32-bit unsigned to 64-bit
	 * 
	 * @return The result.
	 */
	public V128 extendLowU32To64() {
		return new V128(Integer.toUnsignedLong((int)low), Integer.toUnsignedLong((int)(low >>> 32)));
	}

	/**
	 * Extend the low half of the vector from 32-bit signed to 64-bit
	 * 
	 * @return The result.
	 */
	public V128 extendLowS32To64() {
		return new V128((long)((int)low), (long)((int)(low >>> 32)));
	}

	/**
	 * Extend the high half of the vector from 32-bit unsigned to 64-bit
	 * 
	 * @return The result.
	 */
	public V128 extendHighU32To64() {
		return new V128(Integer.toUnsignedLong((int)high), Integer.toUnsignedLong((int)(high >>> 32)));
	}

	/**
	 * Extend the high half of the vector from 32-bit signed to 64-bit
	 * 
	 * @return The result.
	 */
	public V128 extendHighS32To64() {
		return new V128((long)((int)high), (long)((int)(high >>> 32)));
	}

	/**
	 * Convert S32 vector to F32
	 * 
	 * @return The result.
	 */
	public V128 convertS32ToF32() {
		return new V128(
			Integer.toUnsignedLong(Float.floatToRawIntBits((float)(int)low))
				| Integer.toUnsignedLong(Float.floatToRawIntBits((float)(int)(low >>> 32))) << 32,
			Integer.toUnsignedLong(Float.floatToRawIntBits((float)(int)high))
				| Integer.toUnsignedLong(Float.floatToRawIntBits((float)(int)(high >>> 32))) << 32
		);
	}

	/**
	 * Convert U32 vector to F32
	 * 
	 * @return The result.
	 */
	public V128 convertU32ToF32() {
		return new V128(
			Integer.toUnsignedLong(
				Float.floatToRawIntBits((float)Integer.toUnsignedLong((int)low))
			) | Integer.toUnsignedLong(Float.floatToRawIntBits((float)Integer.toUnsignedLong((int)(low >>> 32)))) << 32,
			Integer.toUnsignedLong(
				Float.floatToRawIntBits((float)Integer.toUnsignedLong((int)high))
			) | Integer.toUnsignedLong(Float.floatToRawIntBits((float)Integer.toUnsignedLong((int)(high >>> 32)))) << 32
		);
	}

	/**
	 * Demote F64 vector to F32. High half is zero.
	 * 
	 * @return The result.
	 */
	public V128 demoteF64ToF32Zero() {
		return new V128(
			Integer.toUnsignedLong(Float.floatToRawIntBits((float)Double.longBitsToDouble(low)))
				| Integer.toUnsignedLong(Float.floatToRawIntBits((float)Double.longBitsToDouble(high))) << 32,
			Integer.toUnsignedLong(Float.floatToRawIntBits(0.0f))
				| Integer.toUnsignedLong(Float.floatToRawIntBits(0.0f)) << 32
		);
	}

	/**
	 * Convert low half of S32 vector to F64
	 * 
	 * @return The result.
	 */
	public V128 convertS32LowToF64() {
		return new V128(
			Double.doubleToRawLongBits((double)(int)low),
			Double.doubleToRawLongBits((double)(int)(low >>> 32))
		);
	}

	/**
	 * Convert low half of U32 vector to F64
	 * 
	 * @return The result.
	 */
	public V128 convertU32LowToF64() {
		return new V128(
			Double.doubleToRawLongBits((double)Integer.toUnsignedLong((int)low)),
			Double.doubleToRawLongBits((double)Integer.toUnsignedLong((int)(low >>> 32)))
		);
	}

	/**
	 * Promote the low half of F32 vector to F64
	 * 
	 * @return The result.
	 */
	public V128 promoteF32LowToF64() {
		return new V128(
			Double.doubleToRawLongBits((double)Float.intBitsToFloat((int)low)),
			Double.doubleToRawLongBits((double)Float.intBitsToFloat((int)(low >>> 32)))
		);
	}

	// Sum bits within each byte without allowing carries into adjacent bytes.
	private static long popcntBytes(long value) {
		value -= (value >>> 1) & 0x5555555555555555L;
		value = (value & 0x3333333333333333L) + ((value >>> 2) & 0x3333333333333333L);
		return (value + (value >>> 4)) & 0x0f0f0f0f0f0f0f0fL;
	}

	private static long absLanes(long value, int width, long laneOnes, long lowerBits) {
		long sign = ((value >>> (width - 1)) & laneOnes) * ((1L << width) - 1);
		return subtractLanes(value ^ sign, sign, lowerBits);
	}

	private static long shiftRightSigned(long value, int shift, long mask, int width, long laneOnes) {
		long sign = ((value >>> (width - 1)) & laneOnes) * ((1L << width) - 1);
		return ((value >>> shift) & mask) | (sign & ~mask);
	}

	/**
	 * POPCNT operation for 8-bit.
	 * 
	 * @return The result.
	 */
	public V128 popcnt8() {
		return new V128(popcntBytes(low), popcntBytes(high));
	}

	/**
	 * Swizzle WebAssembly operation for 8-bit.
	 * 
	 * @param indexes
	 *            The indexes.
	 * @return The result.
	 */
	public V128 swizzle8(V128 indexes) {
		return new V128(swizzleHalf(low, high, indexes.low), swizzleHalf(low, high, indexes.high));
	}

	private static long swizzleByte(long low, long high, int index) {
		if(index >= 16)
			return 0;
		return ((index < 8 ? low : high) >>> ((index & 7) * 8)) & 0xffL;
	}

	private static long shuffleByte(long aLow, long aHigh, long bLow, long bHigh, int index) {
		if(index >= 32)
			throw new IllegalArgumentException("Invalid shuffle index: " + index);
		long half = index < 8 ? aLow : index < 16 ? aHigh : index < 24 ? bLow : bHigh;
		return (half >>> ((index & 7) * 8)) & 0xffL;
	}

	private static long swizzleHalf(long low, long high, long indexes) {
		return swizzleByte(low, high, (int)indexes & 0xff) | swizzleByte(low, high, (int)(indexes >>> 8) & 0xff) << 8
			| swizzleByte(low, high, (int)(indexes >>> 16) & 0xff) << 16
			| swizzleByte(low, high, (int)(indexes >>> 24) & 0xff) << 24
			| swizzleByte(low, high, (int)(indexes >>> 32) & 0xff) << 32
			| swizzleByte(low, high, (int)(indexes >>> 40) & 0xff) << 40
			| swizzleByte(low, high, (int)(indexes >>> 48) & 0xff) << 48
			| swizzleByte(low, high, (int)(indexes >>> 56) & 0xff) << 56;
	}

	private static long shuffleHalf(long aLow, long aHigh, long bLow, long bHigh, long indexes) {
		return shuffleByte(aLow, aHigh, bLow, bHigh, (int)indexes & 0xff)
			| shuffleByte(aLow, aHigh, bLow, bHigh, (int)(indexes >>> 8) & 0xff) << 8
			| shuffleByte(aLow, aHigh, bLow, bHigh, (int)(indexes >>> 16) & 0xff) << 16
			| shuffleByte(aLow, aHigh, bLow, bHigh, (int)(indexes >>> 24) & 0xff) << 24
			| shuffleByte(aLow, aHigh, bLow, bHigh, (int)(indexes >>> 32) & 0xff) << 32
			| shuffleByte(aLow, aHigh, bLow, bHigh, (int)(indexes >>> 40) & 0xff) << 40
			| shuffleByte(aLow, aHigh, bLow, bHigh, (int)(indexes >>> 48) & 0xff) << 48
			| shuffleByte(aLow, aHigh, bLow, bHigh, (int)(indexes >>> 56) & 0xff) << 56;
	}

	/**
	 * WebAssembly shuffle operation for 8-bit.
	 * 
	 * @param b
	 *            The second set of values.
	 * @param laneIndexes
	 *            The laneIndexes.
	 * @return The result.
	 */
	public V128 shuffle8(V128 b, V128 laneIndexes) {
		return new V128(
			shuffleHalf(low, high, b.low, b.high, laneIndexes.low),
			shuffleHalf(low, high, b.low, b.high, laneIndexes.high)
		);
	}

	/**
	 * Splat WebAssembly operation for 8-bit.
	 * 
	 * @param value
	 *            The value.
	 * @return The result.
	 */
	public static V128 splat8(byte value) {
		long half = Byte.toUnsignedLong(value) * 0x0101010101010101L;
		return new V128(half, half);
	}

	/**
	 * Splat WebAssembly operation for 8-bit.
	 * 
	 * @param value
	 *            The value.
	 * @return The result.
	 */
	public static V128 splat16(short value) {
		long half = Short.toUnsignedLong(value) * 0x0001000100010001L;
		return new V128(half, half);
	}

	/**
	 * Splat WebAssembly operation for 32-bit.
	 * 
	 * @param value
	 *            The value.
	 * @return The result.
	 */
	public static V128 splat32(int value) {
		long half = Integer.toUnsignedLong(value) * 0x0000000100000001L;
		return new V128(half, half);
	}

	/**
	 * Splat WebAssembly operation for 64-bit.
	 * 
	 * @param value
	 *            The value.
	 * @return The result.
	 */
	public static V128 splat64(long value) {
		long half = value;
		return new V128(half, half);
	}

	/**
	 * Splat WebAssembly operation for 32-bit float.
	 * 
	 * @param value
	 *            The value.
	 * @return The result.
	 */
	public static V128 splatF32(float value) {
		return splat32(Float.floatToRawIntBits(value));
	}

	/**
	 * Splat WebAssembly operation for 64-bit float.
	 * 
	 * @param value
	 *            The value.
	 * @return The result.
	 */
	public static V128 splatF64(double value) {
		return splat64(Double.doubleToRawLongBits(value));
	}

	/**
	 * Replace an 8-bit lane.
	 * 
	 * @param value
	 *            The value.
	 * @param index
	 *            The index.
	 * @return The result.
	 */
	public V128 replaceLane8(byte value, int index) {
		if(index < 0 || index >= 16)
			return this;
		int shift = (index & 7) * 8;
		long mask = 0xffL << shift;
		long bits = Byte.toUnsignedLong(value) << shift;
		return index < 8 ? new V128((low & ~mask) | bits, high) : new V128(low, (high & ~mask) | bits);
	}

	/**
	 * Replace a 16-bit lane.
	 * 
	 * @param value
	 *            The value.
	 * @param index
	 *            The index.
	 * @return The result.
	 */
	public V128 replaceLane16(short value, int index) {
		if(index < 0 || index >= 8)
			return this;
		int shift = (index & 3) * 16;
		long mask = 0xffffL << shift;
		long bits = Short.toUnsignedLong(value) << shift;
		return index < 4 ? new V128((low & ~mask) | bits, high) : new V128(low, (high & ~mask) | bits);
	}

	/**
	 * Replace a 32-bit lane.
	 * 
	 * @param value
	 *            The value.
	 * @param index
	 *            The index.
	 * @return The result.
	 */
	public V128 replaceLane32(int value, int index) {
		if(index < 0 || index >= 4)
			return this;
		int shift = (index & 1) * 32;
		long mask = 0xffffffffL << shift;
		long bits = Integer.toUnsignedLong(value) << shift;
		return index < 2 ? new V128((low & ~mask) | bits, high) : new V128(low, (high & ~mask) | bits);
	}

	/**
	 * Replace a 64-bit lane.
	 * 
	 * @param value
	 *            The value.
	 * @param index
	 *            The index.
	 * @return The result.
	 */
	public V128 replaceLane64(long value, int index) {
		return switch(index) {
			case 0 -> new V128(value, high);
			case 1 -> new V128(low, value);
			default -> this;
		};
	}

	/**
	 * Replace a 32-bit float lane.
	 * 
	 * @param value
	 *            The value.
	 * @param index
	 *            The index.
	 * @return The result.
	 */
	public V128 replaceLaneF32(float value, int index) {
		return replaceLane32(Float.floatToRawIntBits(value), index);
	}

	/**
	 * Replace a 64-bit float lane.
	 * 
	 * @param value
	 *            The value.
	 * @param index
	 *            The index.
	 * @return The result.
	 */
	public V128 replaceLaneF64(double value, int index) {
		return replaceLane64(Double.doubleToRawLongBits(value), index);
	}

	/**
	 * Check if all 8-bit values are true.
	 * 
	 * @return true iff all 8-bit values are true.
	 */
	public boolean allTrue8() {
		// Subtracting one per byte exposes zero lanes in their sign bits.
		return ((((low - 0x0101010101010101L) & ~low) | ((high - 0x0101010101010101L) & ~high))
			& 0x8080808080808080L) == 0;
	}

	/**
	 * Check if all 16-bit values are true.
	 * 
	 * @return true iff all 16-bit values are true.
	 */
	public boolean allTrue16() {
		return ((((low - 0x0001000100010001L) & ~low) | ((high - 0x0001000100010001L) & ~high))
			& 0x8000800080008000L) == 0;
	}

	/**
	 * Check if all 32-bit values are true.
	 * 
	 * @return true iff all 32-bit values are true.
	 */
	public boolean allTrue32() {
		return (int)low != 0 && (int)(low >>> 32) != 0 && (int)high != 0 && (int)(high >>> 32) != 0;
	}

	/**
	 * Check if all 64-bit values are true.
	 * 
	 * @return true iff all 64-bit values are true.
	 */
	public boolean allTrue64() {
		return low != 0 && high != 0;
	}

	/**
	 * WebAssembly bitmask operation for 8-bit values.
	 * 
	 * @return The result.
	 */
	public int bitmask8() {
		return (int)(((low >>> 7) & 1L) | (((low >>> 15) & 1L) << 1) | (((low >>> 23) & 1L) << 2)
			| (((low >>> 31) & 1L) << 3) | (((low >>> 39) & 1L) << 4) | (((low >>> 47) & 1L) << 5)
			| (((low >>> 55) & 1L) << 6) | (((low >>> 63) & 1L) << 7) | (((high >>> 7) & 1L) << 8)
			| (((high >>> 15) & 1L) << 9) | (((high >>> 23) & 1L) << 10) | (((high >>> 31) & 1L) << 11)
			| (((high >>> 39) & 1L) << 12) | (((high >>> 47) & 1L) << 13) | (((high >>> 55) & 1L) << 14)
			| (((high >>> 63) & 1L) << 15));
	}

	/**
	 * WebAssembly bitmask operation for 16-bit values.
	 * 
	 * @return The result.
	 */
	public int bitmask16() {
		return (int)(((low >>> 15) & 1L) | (((low >>> 31) & 1L) << 1) | (((low >>> 47) & 1L) << 2)
			| (((low >>> 63) & 1L) << 3) | (((high >>> 15) & 1L) << 4) | (((high >>> 31) & 1L) << 5)
			| (((high >>> 47) & 1L) << 6) | (((high >>> 63) & 1L) << 7));
	}

	/**
	 * WebAssembly bitmask operation for 32-bit values.
	 * 
	 * @return The result.
	 */
	public int bitmask32() {
		return (int)(((low >>> 31) & 1L) | (((low >>> 63) & 1L) << 1) | (((high >>> 31) & 1L) << 2)
			| (((high >>> 63) & 1L) << 3));
	}

	/**
	 * WebAssembly bitmask operation for 64-bit values.
	 * 
	 * @return The result.
	 */
	public int bitmask64() {
		return (int)(((low >>> 63) & 1L) | (((high >>> 63) & 1L) << 1));
	}

	/**
	 * Implements the webassembly i16x8.q15mulr_sat_s operation.
	 * 
	 * @param b
	 *            The second operand.
	 * @return The result.
	 */
	public V128 q15mulrSatS(V128 b) {
		return new V128(q15mulrSatSHalf(low, b.low), q15mulrSatSHalf(high, b.high));
	}

	private static long q15mulrSatSHalf(long a, long b) {
		short lane0 = Util.narrowS32I16(((short)a * (short)b + (1 << 14)) >> 15);
		short lane1 = Util.narrowS32I16(((short)(a >>> 16) * (short)(b >>> 16) + (1 << 14)) >> 15);
		short lane2 = Util.narrowS32I16(((short)(a >>> 32) * (short)(b >>> 32) + (1 << 14)) >> 15);
		short lane3 = Util.narrowS32I16(((short)(a >>> 48) * (short)(b >>> 48) + (1 << 14)) >> 15);
		return Short.toUnsignedLong(lane0) | Short.toUnsignedLong(lane1) << 16 | Short.toUnsignedLong(lane2) << 32
			| Short.toUnsignedLong(lane3) << 48;
	}

	/**
	 * Implements the webassembly i32x4.dot_i16x8 operation.
	 * 
	 * @param b
	 *            The second operand.
	 * @return The result.
	 */
	public V128 dotS16x8(V128 b) {
		return new V128(
			Integer.toUnsignedLong((short)low * (short)b.low + (short)(low >>> 16) * (short)(b.low >>> 16))
				| Integer.toUnsignedLong(
					(short)(low >>> 32) * (short)(b.low >>> 32) + (short)(low >>> 48) * (short)(b.low >>> 48)
				) << 32,
			Integer.toUnsignedLong((short)high * (short)b.high + (short)(high >>> 16) * (short)(b.high >>> 16))
				| Integer.toUnsignedLong(
					(short)(high >>> 32) * (short)(b.high >>> 32) + (short)(high >>> 48) * (short)(b.high >>> 48)
				) << 32
		);
	}

	/**
	 * Implements the webassembly i16x8.relaxed_dot_i8x16_i7x16_s operation.
	 * 
	 * @param b
	 *            The second operand.
	 * @return The result.
	 */
	public V128 relaxedDotI8x16ByI7x16Signed(V128 b) {
		return new V128(
			Short
				.toUnsignedLong(
					Util.addSatS16((short)((byte)low * (byte)b.low), (short)((byte)(low >>> 8) * (byte)(b.low >>> 8)))
				)
				| Short.toUnsignedLong(
					Util.addSatS16(
						(short)((byte)(low >>> 16) * (byte)(b.low >>> 16)),
						(short)((byte)(low >>> 24) * (byte)(b.low >>> 24))
					)
				) << 16
				| Short.toUnsignedLong(
					Util.addSatS16(
						(short)((byte)(low >>> 32) * (byte)(b.low >>> 32)),
						(short)((byte)(low >>> 40) * (byte)(b.low >>> 40))
					)
				) << 32
				| Short.toUnsignedLong(
					Util.addSatS16(
						(short)((byte)(low >>> 48) * (byte)(b.low >>> 48)),
						(short)((byte)(low >>> 56) * (byte)(b.low >>> 56))
					)
				) << 48,
			Short
				.toUnsignedLong(
					Util.addSatS16(
						(short)((byte)high * (byte)b.high),
						(short)((byte)(high >>> 8) * (byte)(b.high >>> 8))
					)
				)
				| Short.toUnsignedLong(
					Util.addSatS16(
						(short)((byte)(high >>> 16) * (byte)(b.high >>> 16)),
						(short)((byte)(high >>> 24) * (byte)(b.high >>> 24))
					)
				) << 16
				| Short.toUnsignedLong(
					Util.addSatS16(
						(short)((byte)(high >>> 32) * (byte)(b.high >>> 32)),
						(short)((byte)(high >>> 40) * (byte)(b.high >>> 40))
					)
				) << 32
				| Short.toUnsignedLong(
					Util.addSatS16(
						(short)((byte)(high >>> 48) * (byte)(b.high >>> 48)),
						(short)((byte)(high >>> 56) * (byte)(b.high >>> 56))
					)
				) << 48
		);
	}

	/**
	 * Implements the webassembly i16x8.relaxed_dot_i8x16_i7x16_add_s operation.
	 * 
	 * @param b
	 *            The second operand.
	 * @param c
	 *            The third operand.
	 * @return The result.
	 */
	public V128 relaxedDotI8x16ByI7x16AddSigned(V128 b, V128 c) {
		return new V128(
			Integer
				.toUnsignedLong(
					(byte)low * (byte)b.low + (byte)(low >>> 8) * (byte)(b.low >>> 8)
						+ (byte)(low >>> 16) * (byte)(b.low >>> 16) + (byte)(low >>> 24) * (byte)(b.low >>> 24)
						+ (int)c.low
				)
				| Integer.toUnsignedLong(
					(byte)(low >>> 32) * (byte)(b.low >>> 32) + (byte)(low >>> 40) * (byte)(b.low >>> 40)
						+ (byte)(low >>> 48) * (byte)(b.low >>> 48) + (byte)(low >>> 56) * (byte)(b.low >>> 56)
						+ (int)(c.low >>> 32)
				) << 32,
			Integer
				.toUnsignedLong(
					(byte)high * (byte)b.high + (byte)(high >>> 8) * (byte)(b.high >>> 8)
						+ (byte)(high >>> 16) * (byte)(b.high >>> 16) + (byte)(high >>> 24) * (byte)(b.high >>> 24)
						+ (int)c.high
				)
				| Integer.toUnsignedLong(
					(byte)(high >>> 32) * (byte)(b.high >>> 32) + (byte)(high >>> 40) * (byte)(b.high >>> 40)
						+ (byte)(high >>> 48) * (byte)(b.high >>> 48) + (byte)(high >>> 56) * (byte)(b.high >>> 56)
						+ (int)(c.high >>> 32)
				) << 32
		);
	}

	/**
	 * Compares two V128 vectors for equality across 16 lanes of 8-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if equal, 0 if not equal
	 */
	public V128 equals8(V128 b) {
		return new V128(equals8Half(low, b.low), equals8Half(high, b.high));
	}

	private static long equals8Half(long a, long b) {
		byte lane0 = (byte)a == (byte)b ? (byte)-1 : (byte)0;
		byte lane1 = (byte)(a >>> 8) == (byte)(b >>> 8) ? (byte)-1 : (byte)0;
		byte lane2 = (byte)(a >>> 16) == (byte)(b >>> 16) ? (byte)-1 : (byte)0;
		byte lane3 = (byte)(a >>> 24) == (byte)(b >>> 24) ? (byte)-1 : (byte)0;
		byte lane4 = (byte)(a >>> 32) == (byte)(b >>> 32) ? (byte)-1 : (byte)0;
		byte lane5 = (byte)(a >>> 40) == (byte)(b >>> 40) ? (byte)-1 : (byte)0;
		byte lane6 = (byte)(a >>> 48) == (byte)(b >>> 48) ? (byte)-1 : (byte)0;
		byte lane7 = (byte)(a >>> 56) == (byte)(b >>> 56) ? (byte)-1 : (byte)0;
		return Byte.toUnsignedLong(lane0) | Byte.toUnsignedLong(lane1) << 8 | Byte.toUnsignedLong(lane2) << 16
			| Byte.toUnsignedLong(lane3) << 24 | Byte.toUnsignedLong(lane4) << 32 | Byte.toUnsignedLong(lane5) << 40
			| Byte.toUnsignedLong(lane6) << 48 | Byte.toUnsignedLong(lane7) << 56;
	}

	/**
	 * Compares two V128 vectors for inequality across 16 lanes of 8-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if not equal, 0 if equal
	 */
	public V128 notEquals8(V128 b) {
		return new V128(notEquals8Half(low, b.low), notEquals8Half(high, b.high));
	}

	private static long notEquals8Half(long a, long b) {
		byte lane0 = (byte)a != (byte)b ? (byte)-1 : (byte)0;
		byte lane1 = (byte)(a >>> 8) != (byte)(b >>> 8) ? (byte)-1 : (byte)0;
		byte lane2 = (byte)(a >>> 16) != (byte)(b >>> 16) ? (byte)-1 : (byte)0;
		byte lane3 = (byte)(a >>> 24) != (byte)(b >>> 24) ? (byte)-1 : (byte)0;
		byte lane4 = (byte)(a >>> 32) != (byte)(b >>> 32) ? (byte)-1 : (byte)0;
		byte lane5 = (byte)(a >>> 40) != (byte)(b >>> 40) ? (byte)-1 : (byte)0;
		byte lane6 = (byte)(a >>> 48) != (byte)(b >>> 48) ? (byte)-1 : (byte)0;
		byte lane7 = (byte)(a >>> 56) != (byte)(b >>> 56) ? (byte)-1 : (byte)0;
		return Byte.toUnsignedLong(lane0) | Byte.toUnsignedLong(lane1) << 8 | Byte.toUnsignedLong(lane2) << 16
			| Byte.toUnsignedLong(lane3) << 24 | Byte.toUnsignedLong(lane4) << 32 | Byte.toUnsignedLong(lane5) << 40
			| Byte.toUnsignedLong(lane6) << 48 | Byte.toUnsignedLong(lane7) << 56;
	}

	/**
	 * Performs signed less-than comparison across 16 lanes of 8-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than, 0 otherwise
	 */
	public V128 lessThanSigned8(V128 b) {
		return new V128(lessThanSigned8Half(low, b.low), lessThanSigned8Half(high, b.high));
	}

	private static long lessThanSigned8Half(long a, long b) {
		byte lane0 = (byte)a < (byte)b ? (byte)-1 : (byte)0;
		byte lane1 = (byte)(a >>> 8) < (byte)(b >>> 8) ? (byte)-1 : (byte)0;
		byte lane2 = (byte)(a >>> 16) < (byte)(b >>> 16) ? (byte)-1 : (byte)0;
		byte lane3 = (byte)(a >>> 24) < (byte)(b >>> 24) ? (byte)-1 : (byte)0;
		byte lane4 = (byte)(a >>> 32) < (byte)(b >>> 32) ? (byte)-1 : (byte)0;
		byte lane5 = (byte)(a >>> 40) < (byte)(b >>> 40) ? (byte)-1 : (byte)0;
		byte lane6 = (byte)(a >>> 48) < (byte)(b >>> 48) ? (byte)-1 : (byte)0;
		byte lane7 = (byte)(a >>> 56) < (byte)(b >>> 56) ? (byte)-1 : (byte)0;
		return Byte.toUnsignedLong(lane0) | Byte.toUnsignedLong(lane1) << 8 | Byte.toUnsignedLong(lane2) << 16
			| Byte.toUnsignedLong(lane3) << 24 | Byte.toUnsignedLong(lane4) << 32 | Byte.toUnsignedLong(lane5) << 40
			| Byte.toUnsignedLong(lane6) << 48 | Byte.toUnsignedLong(lane7) << 56;
	}

	/**
	 * Performs unsigned less-than comparison across 16 lanes of 8-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than, 0 otherwise
	 */
	public V128 lessThanUnsigned8(V128 b) {
		return new V128(lessThanUnsigned8Half(low, b.low), lessThanUnsigned8Half(high, b.high));
	}

	private static long lessThanUnsigned8Half(long a, long b) {
		byte lane0 = Byte.compareUnsigned((byte)a, (byte)b) < 0 ? (byte)-1 : (byte)0;
		byte lane1 = Byte.compareUnsigned((byte)(a >>> 8), (byte)(b >>> 8)) < 0 ? (byte)-1 : (byte)0;
		byte lane2 = Byte.compareUnsigned((byte)(a >>> 16), (byte)(b >>> 16)) < 0 ? (byte)-1 : (byte)0;
		byte lane3 = Byte.compareUnsigned((byte)(a >>> 24), (byte)(b >>> 24)) < 0 ? (byte)-1 : (byte)0;
		byte lane4 = Byte.compareUnsigned((byte)(a >>> 32), (byte)(b >>> 32)) < 0 ? (byte)-1 : (byte)0;
		byte lane5 = Byte.compareUnsigned((byte)(a >>> 40), (byte)(b >>> 40)) < 0 ? (byte)-1 : (byte)0;
		byte lane6 = Byte.compareUnsigned((byte)(a >>> 48), (byte)(b >>> 48)) < 0 ? (byte)-1 : (byte)0;
		byte lane7 = Byte.compareUnsigned((byte)(a >>> 56), (byte)(b >>> 56)) < 0 ? (byte)-1 : (byte)0;
		return Byte.toUnsignedLong(lane0) | Byte.toUnsignedLong(lane1) << 8 | Byte.toUnsignedLong(lane2) << 16
			| Byte.toUnsignedLong(lane3) << 24 | Byte.toUnsignedLong(lane4) << 32 | Byte.toUnsignedLong(lane5) << 40
			| Byte.toUnsignedLong(lane6) << 48 | Byte.toUnsignedLong(lane7) << 56;
	}

	/**
	 * Performs signed greater-than comparison across 16 lanes of 8-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than, 0 otherwise
	 */
	public V128 greaterThanSigned8(V128 b) {
		return new V128(greaterThanSigned8Half(low, b.low), greaterThanSigned8Half(high, b.high));
	}

	private static long greaterThanSigned8Half(long a, long b) {
		byte lane0 = (byte)a > (byte)b ? (byte)-1 : (byte)0;
		byte lane1 = (byte)(a >>> 8) > (byte)(b >>> 8) ? (byte)-1 : (byte)0;
		byte lane2 = (byte)(a >>> 16) > (byte)(b >>> 16) ? (byte)-1 : (byte)0;
		byte lane3 = (byte)(a >>> 24) > (byte)(b >>> 24) ? (byte)-1 : (byte)0;
		byte lane4 = (byte)(a >>> 32) > (byte)(b >>> 32) ? (byte)-1 : (byte)0;
		byte lane5 = (byte)(a >>> 40) > (byte)(b >>> 40) ? (byte)-1 : (byte)0;
		byte lane6 = (byte)(a >>> 48) > (byte)(b >>> 48) ? (byte)-1 : (byte)0;
		byte lane7 = (byte)(a >>> 56) > (byte)(b >>> 56) ? (byte)-1 : (byte)0;
		return Byte.toUnsignedLong(lane0) | Byte.toUnsignedLong(lane1) << 8 | Byte.toUnsignedLong(lane2) << 16
			| Byte.toUnsignedLong(lane3) << 24 | Byte.toUnsignedLong(lane4) << 32 | Byte.toUnsignedLong(lane5) << 40
			| Byte.toUnsignedLong(lane6) << 48 | Byte.toUnsignedLong(lane7) << 56;
	}

	/**
	 * Performs unsigned greater-than comparison across 16 lanes of 8-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than, 0 otherwise
	 */
	public V128 greaterThanUnsigned8(V128 b) {
		return new V128(greaterThanUnsigned8Half(low, b.low), greaterThanUnsigned8Half(high, b.high));
	}

	private static long greaterThanUnsigned8Half(long a, long b) {
		byte lane0 = Byte.compareUnsigned((byte)a, (byte)b) > 0 ? (byte)-1 : (byte)0;
		byte lane1 = Byte.compareUnsigned((byte)(a >>> 8), (byte)(b >>> 8)) > 0 ? (byte)-1 : (byte)0;
		byte lane2 = Byte.compareUnsigned((byte)(a >>> 16), (byte)(b >>> 16)) > 0 ? (byte)-1 : (byte)0;
		byte lane3 = Byte.compareUnsigned((byte)(a >>> 24), (byte)(b >>> 24)) > 0 ? (byte)-1 : (byte)0;
		byte lane4 = Byte.compareUnsigned((byte)(a >>> 32), (byte)(b >>> 32)) > 0 ? (byte)-1 : (byte)0;
		byte lane5 = Byte.compareUnsigned((byte)(a >>> 40), (byte)(b >>> 40)) > 0 ? (byte)-1 : (byte)0;
		byte lane6 = Byte.compareUnsigned((byte)(a >>> 48), (byte)(b >>> 48)) > 0 ? (byte)-1 : (byte)0;
		byte lane7 = Byte.compareUnsigned((byte)(a >>> 56), (byte)(b >>> 56)) > 0 ? (byte)-1 : (byte)0;
		return Byte.toUnsignedLong(lane0) | Byte.toUnsignedLong(lane1) << 8 | Byte.toUnsignedLong(lane2) << 16
			| Byte.toUnsignedLong(lane3) << 24 | Byte.toUnsignedLong(lane4) << 32 | Byte.toUnsignedLong(lane5) << 40
			| Byte.toUnsignedLong(lane6) << 48 | Byte.toUnsignedLong(lane7) << 56;
	}

	/**
	 * Performs signed less-than-or-equal comparison across 16 lanes of 8-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than or equal, 0
	 *         otherwise
	 */
	public V128 lessThanOrEqualSigned8(V128 b) {
		return new V128(lessThanOrEqualSigned8Half(low, b.low), lessThanOrEqualSigned8Half(high, b.high));
	}

	private static long lessThanOrEqualSigned8Half(long a, long b) {
		byte lane0 = (byte)a <= (byte)b ? (byte)-1 : (byte)0;
		byte lane1 = (byte)(a >>> 8) <= (byte)(b >>> 8) ? (byte)-1 : (byte)0;
		byte lane2 = (byte)(a >>> 16) <= (byte)(b >>> 16) ? (byte)-1 : (byte)0;
		byte lane3 = (byte)(a >>> 24) <= (byte)(b >>> 24) ? (byte)-1 : (byte)0;
		byte lane4 = (byte)(a >>> 32) <= (byte)(b >>> 32) ? (byte)-1 : (byte)0;
		byte lane5 = (byte)(a >>> 40) <= (byte)(b >>> 40) ? (byte)-1 : (byte)0;
		byte lane6 = (byte)(a >>> 48) <= (byte)(b >>> 48) ? (byte)-1 : (byte)0;
		byte lane7 = (byte)(a >>> 56) <= (byte)(b >>> 56) ? (byte)-1 : (byte)0;
		return Byte.toUnsignedLong(lane0) | Byte.toUnsignedLong(lane1) << 8 | Byte.toUnsignedLong(lane2) << 16
			| Byte.toUnsignedLong(lane3) << 24 | Byte.toUnsignedLong(lane4) << 32 | Byte.toUnsignedLong(lane5) << 40
			| Byte.toUnsignedLong(lane6) << 48 | Byte.toUnsignedLong(lane7) << 56;
	}

	/**
	 * Performs unsigned less-than-or-equal comparison across 16 lanes of 8-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than or equal, 0
	 *         otherwise
	 */
	public V128 lessThanOrEqualUnsigned8(V128 b) {
		return new V128(lessThanOrEqualUnsigned8Half(low, b.low), lessThanOrEqualUnsigned8Half(high, b.high));
	}

	private static long lessThanOrEqualUnsigned8Half(long a, long b) {
		byte lane0 = Byte.compareUnsigned((byte)a, (byte)b) <= 0 ? (byte)-1 : (byte)0;
		byte lane1 = Byte.compareUnsigned((byte)(a >>> 8), (byte)(b >>> 8)) <= 0 ? (byte)-1 : (byte)0;
		byte lane2 = Byte.compareUnsigned((byte)(a >>> 16), (byte)(b >>> 16)) <= 0 ? (byte)-1 : (byte)0;
		byte lane3 = Byte.compareUnsigned((byte)(a >>> 24), (byte)(b >>> 24)) <= 0 ? (byte)-1 : (byte)0;
		byte lane4 = Byte.compareUnsigned((byte)(a >>> 32), (byte)(b >>> 32)) <= 0 ? (byte)-1 : (byte)0;
		byte lane5 = Byte.compareUnsigned((byte)(a >>> 40), (byte)(b >>> 40)) <= 0 ? (byte)-1 : (byte)0;
		byte lane6 = Byte.compareUnsigned((byte)(a >>> 48), (byte)(b >>> 48)) <= 0 ? (byte)-1 : (byte)0;
		byte lane7 = Byte.compareUnsigned((byte)(a >>> 56), (byte)(b >>> 56)) <= 0 ? (byte)-1 : (byte)0;
		return Byte.toUnsignedLong(lane0) | Byte.toUnsignedLong(lane1) << 8 | Byte.toUnsignedLong(lane2) << 16
			| Byte.toUnsignedLong(lane3) << 24 | Byte.toUnsignedLong(lane4) << 32 | Byte.toUnsignedLong(lane5) << 40
			| Byte.toUnsignedLong(lane6) << 48 | Byte.toUnsignedLong(lane7) << 56;
	}

	/**
	 * Performs signed greater-than-or-equal comparison across 16 lanes of 8-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than or equal, 0
	 *         otherwise
	 */
	public V128 greaterThanOrEqualSigned8(V128 b) {
		return new V128(greaterThanOrEqualSigned8Half(low, b.low), greaterThanOrEqualSigned8Half(high, b.high));
	}

	private static long greaterThanOrEqualSigned8Half(long a, long b) {
		byte lane0 = (byte)a >= (byte)b ? (byte)-1 : (byte)0;
		byte lane1 = (byte)(a >>> 8) >= (byte)(b >>> 8) ? (byte)-1 : (byte)0;
		byte lane2 = (byte)(a >>> 16) >= (byte)(b >>> 16) ? (byte)-1 : (byte)0;
		byte lane3 = (byte)(a >>> 24) >= (byte)(b >>> 24) ? (byte)-1 : (byte)0;
		byte lane4 = (byte)(a >>> 32) >= (byte)(b >>> 32) ? (byte)-1 : (byte)0;
		byte lane5 = (byte)(a >>> 40) >= (byte)(b >>> 40) ? (byte)-1 : (byte)0;
		byte lane6 = (byte)(a >>> 48) >= (byte)(b >>> 48) ? (byte)-1 : (byte)0;
		byte lane7 = (byte)(a >>> 56) >= (byte)(b >>> 56) ? (byte)-1 : (byte)0;
		return Byte.toUnsignedLong(lane0) | Byte.toUnsignedLong(lane1) << 8 | Byte.toUnsignedLong(lane2) << 16
			| Byte.toUnsignedLong(lane3) << 24 | Byte.toUnsignedLong(lane4) << 32 | Byte.toUnsignedLong(lane5) << 40
			| Byte.toUnsignedLong(lane6) << 48 | Byte.toUnsignedLong(lane7) << 56;
	}

	/**
	 * Performs unsigned greater-than-or-equal comparison across 16 lanes of 8-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than or equal, 0
	 *         otherwise
	 */
	public V128 greaterThanOrEqualUnsigned8(V128 b) {
		return new V128(greaterThanOrEqualUnsigned8Half(low, b.low), greaterThanOrEqualUnsigned8Half(high, b.high));
	}

	private static long greaterThanOrEqualUnsigned8Half(long a, long b) {
		byte lane0 = Byte.compareUnsigned((byte)a, (byte)b) >= 0 ? (byte)-1 : (byte)0;
		byte lane1 = Byte.compareUnsigned((byte)(a >>> 8), (byte)(b >>> 8)) >= 0 ? (byte)-1 : (byte)0;
		byte lane2 = Byte.compareUnsigned((byte)(a >>> 16), (byte)(b >>> 16)) >= 0 ? (byte)-1 : (byte)0;
		byte lane3 = Byte.compareUnsigned((byte)(a >>> 24), (byte)(b >>> 24)) >= 0 ? (byte)-1 : (byte)0;
		byte lane4 = Byte.compareUnsigned((byte)(a >>> 32), (byte)(b >>> 32)) >= 0 ? (byte)-1 : (byte)0;
		byte lane5 = Byte.compareUnsigned((byte)(a >>> 40), (byte)(b >>> 40)) >= 0 ? (byte)-1 : (byte)0;
		byte lane6 = Byte.compareUnsigned((byte)(a >>> 48), (byte)(b >>> 48)) >= 0 ? (byte)-1 : (byte)0;
		byte lane7 = Byte.compareUnsigned((byte)(a >>> 56), (byte)(b >>> 56)) >= 0 ? (byte)-1 : (byte)0;
		return Byte.toUnsignedLong(lane0) | Byte.toUnsignedLong(lane1) << 8 | Byte.toUnsignedLong(lane2) << 16
			| Byte.toUnsignedLong(lane3) << 24 | Byte.toUnsignedLong(lane4) << 32 | Byte.toUnsignedLong(lane5) << 40
			| Byte.toUnsignedLong(lane6) << 48 | Byte.toUnsignedLong(lane7) << 56;
	}

	/**
	 * Compares two V128 vectors for equality across 8 lanes of 16-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if equal, 0 if not equal
	 */
	public V128 equals16(V128 b) {
		return new V128(equals16Half(low, b.low), equals16Half(high, b.high));
	}

	private static long equals16Half(long a, long b) {
		short lane0 = (short)a == (short)b ? (short)-1 : (short)0;
		short lane1 = (short)(a >>> 16) == (short)(b >>> 16) ? (short)-1 : (short)0;
		short lane2 = (short)(a >>> 32) == (short)(b >>> 32) ? (short)-1 : (short)0;
		short lane3 = (short)(a >>> 48) == (short)(b >>> 48) ? (short)-1 : (short)0;
		return Short.toUnsignedLong(lane0) | Short.toUnsignedLong(lane1) << 16 | Short.toUnsignedLong(lane2) << 32
			| Short.toUnsignedLong(lane3) << 48;
	}

	/**
	 * Compares two V128 vectors for inequality across 8 lanes of 16-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if not equal, 0 if equal
	 */
	public V128 notEquals16(V128 b) {
		return new V128(notEquals16Half(low, b.low), notEquals16Half(high, b.high));
	}

	private static long notEquals16Half(long a, long b) {
		short lane0 = (short)a != (short)b ? (short)-1 : (short)0;
		short lane1 = (short)(a >>> 16) != (short)(b >>> 16) ? (short)-1 : (short)0;
		short lane2 = (short)(a >>> 32) != (short)(b >>> 32) ? (short)-1 : (short)0;
		short lane3 = (short)(a >>> 48) != (short)(b >>> 48) ? (short)-1 : (short)0;
		return Short.toUnsignedLong(lane0) | Short.toUnsignedLong(lane1) << 16 | Short.toUnsignedLong(lane2) << 32
			| Short.toUnsignedLong(lane3) << 48;
	}

	/**
	 * Performs signed less-than comparison across 8 lanes of 16-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than, 0 otherwise
	 */
	public V128 lessThanSigned16(V128 b) {
		return new V128(lessThanSigned16Half(low, b.low), lessThanSigned16Half(high, b.high));
	}

	private static long lessThanSigned16Half(long a, long b) {
		short lane0 = (short)a < (short)b ? (short)-1 : (short)0;
		short lane1 = (short)(a >>> 16) < (short)(b >>> 16) ? (short)-1 : (short)0;
		short lane2 = (short)(a >>> 32) < (short)(b >>> 32) ? (short)-1 : (short)0;
		short lane3 = (short)(a >>> 48) < (short)(b >>> 48) ? (short)-1 : (short)0;
		return Short.toUnsignedLong(lane0) | Short.toUnsignedLong(lane1) << 16 | Short.toUnsignedLong(lane2) << 32
			| Short.toUnsignedLong(lane3) << 48;
	}

	/**
	 * Performs unsigned less-than comparison across 8 lanes of 16-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than, 0 otherwise
	 */
	public V128 lessThanUnsigned16(V128 b) {
		return new V128(lessThanUnsigned16Half(low, b.low), lessThanUnsigned16Half(high, b.high));
	}

	private static long lessThanUnsigned16Half(long a, long b) {
		short lane0 = Short.compareUnsigned((short)a, (short)b) < 0 ? (short)-1 : (short)0;
		short lane1 = Short.compareUnsigned((short)(a >>> 16), (short)(b >>> 16)) < 0 ? (short)-1 : (short)0;
		short lane2 = Short.compareUnsigned((short)(a >>> 32), (short)(b >>> 32)) < 0 ? (short)-1 : (short)0;
		short lane3 = Short.compareUnsigned((short)(a >>> 48), (short)(b >>> 48)) < 0 ? (short)-1 : (short)0;
		return Short.toUnsignedLong(lane0) | Short.toUnsignedLong(lane1) << 16 | Short.toUnsignedLong(lane2) << 32
			| Short.toUnsignedLong(lane3) << 48;
	}

	/**
	 * Performs signed greater-than comparison across 8 lanes of 16-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than, 0 otherwise
	 */
	public V128 greaterThanSigned16(V128 b) {
		return new V128(greaterThanSigned16Half(low, b.low), greaterThanSigned16Half(high, b.high));
	}

	private static long greaterThanSigned16Half(long a, long b) {
		short lane0 = (short)a > (short)b ? (short)-1 : (short)0;
		short lane1 = (short)(a >>> 16) > (short)(b >>> 16) ? (short)-1 : (short)0;
		short lane2 = (short)(a >>> 32) > (short)(b >>> 32) ? (short)-1 : (short)0;
		short lane3 = (short)(a >>> 48) > (short)(b >>> 48) ? (short)-1 : (short)0;
		return Short.toUnsignedLong(lane0) | Short.toUnsignedLong(lane1) << 16 | Short.toUnsignedLong(lane2) << 32
			| Short.toUnsignedLong(lane3) << 48;
	}

	/**
	 * Performs unsigned greater-than comparison across 8 lanes of 16-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than, 0 otherwise
	 */
	public V128 greaterThanUnsigned16(V128 b) {
		return new V128(greaterThanUnsigned16Half(low, b.low), greaterThanUnsigned16Half(high, b.high));
	}

	private static long greaterThanUnsigned16Half(long a, long b) {
		short lane0 = Short.compareUnsigned((short)a, (short)b) > 0 ? (short)-1 : (short)0;
		short lane1 = Short.compareUnsigned((short)(a >>> 16), (short)(b >>> 16)) > 0 ? (short)-1 : (short)0;
		short lane2 = Short.compareUnsigned((short)(a >>> 32), (short)(b >>> 32)) > 0 ? (short)-1 : (short)0;
		short lane3 = Short.compareUnsigned((short)(a >>> 48), (short)(b >>> 48)) > 0 ? (short)-1 : (short)0;
		return Short.toUnsignedLong(lane0) | Short.toUnsignedLong(lane1) << 16 | Short.toUnsignedLong(lane2) << 32
			| Short.toUnsignedLong(lane3) << 48;
	}

	/**
	 * Performs signed less-than-or-equal comparison across 8 lanes of 16-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than or equal, 0
	 *         otherwise
	 */
	public V128 lessThanOrEqualSigned16(V128 b) {
		return new V128(lessThanOrEqualSigned16Half(low, b.low), lessThanOrEqualSigned16Half(high, b.high));
	}

	private static long lessThanOrEqualSigned16Half(long a, long b) {
		short lane0 = (short)a <= (short)b ? (short)-1 : (short)0;
		short lane1 = (short)(a >>> 16) <= (short)(b >>> 16) ? (short)-1 : (short)0;
		short lane2 = (short)(a >>> 32) <= (short)(b >>> 32) ? (short)-1 : (short)0;
		short lane3 = (short)(a >>> 48) <= (short)(b >>> 48) ? (short)-1 : (short)0;
		return Short.toUnsignedLong(lane0) | Short.toUnsignedLong(lane1) << 16 | Short.toUnsignedLong(lane2) << 32
			| Short.toUnsignedLong(lane3) << 48;
	}

	/**
	 * Performs unsigned less-than-or-equal comparison across 8 lanes of 16-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than or equal, 0
	 *         otherwise
	 */
	public V128 lessThanOrEqualUnsigned16(V128 b) {
		return new V128(lessThanOrEqualUnsigned16Half(low, b.low), lessThanOrEqualUnsigned16Half(high, b.high));
	}

	private static long lessThanOrEqualUnsigned16Half(long a, long b) {
		short lane0 = Short.compareUnsigned((short)a, (short)b) <= 0 ? (short)-1 : (short)0;
		short lane1 = Short.compareUnsigned((short)(a >>> 16), (short)(b >>> 16)) <= 0 ? (short)-1 : (short)0;
		short lane2 = Short.compareUnsigned((short)(a >>> 32), (short)(b >>> 32)) <= 0 ? (short)-1 : (short)0;
		short lane3 = Short.compareUnsigned((short)(a >>> 48), (short)(b >>> 48)) <= 0 ? (short)-1 : (short)0;
		return Short.toUnsignedLong(lane0) | Short.toUnsignedLong(lane1) << 16 | Short.toUnsignedLong(lane2) << 32
			| Short.toUnsignedLong(lane3) << 48;
	}

	/**
	 * Performs signed greater-than-or-equal comparison across 8 lanes of 16-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than or equal, 0
	 *         otherwise
	 */
	public V128 greaterThanOrEqualSigned16(V128 b) {
		return new V128(greaterThanOrEqualSigned16Half(low, b.low), greaterThanOrEqualSigned16Half(high, b.high));
	}

	private static long greaterThanOrEqualSigned16Half(long a, long b) {
		short lane0 = (short)a >= (short)b ? (short)-1 : (short)0;
		short lane1 = (short)(a >>> 16) >= (short)(b >>> 16) ? (short)-1 : (short)0;
		short lane2 = (short)(a >>> 32) >= (short)(b >>> 32) ? (short)-1 : (short)0;
		short lane3 = (short)(a >>> 48) >= (short)(b >>> 48) ? (short)-1 : (short)0;
		return Short.toUnsignedLong(lane0) | Short.toUnsignedLong(lane1) << 16 | Short.toUnsignedLong(lane2) << 32
			| Short.toUnsignedLong(lane3) << 48;
	}

	/**
	 * Performs unsigned greater-than-or-equal comparison across 8 lanes of 16-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than or equal, 0
	 *         otherwise
	 */
	public V128 greaterThanOrEqualUnsigned16(V128 b) {
		return new V128(greaterThanOrEqualUnsigned16Half(low, b.low), greaterThanOrEqualUnsigned16Half(high, b.high));
	}

	private static long greaterThanOrEqualUnsigned16Half(long a, long b) {
		short lane0 = Short.compareUnsigned((short)a, (short)b) >= 0 ? (short)-1 : (short)0;
		short lane1 = Short.compareUnsigned((short)(a >>> 16), (short)(b >>> 16)) >= 0 ? (short)-1 : (short)0;
		short lane2 = Short.compareUnsigned((short)(a >>> 32), (short)(b >>> 32)) >= 0 ? (short)-1 : (short)0;
		short lane3 = Short.compareUnsigned((short)(a >>> 48), (short)(b >>> 48)) >= 0 ? (short)-1 : (short)0;
		return Short.toUnsignedLong(lane0) | Short.toUnsignedLong(lane1) << 16 | Short.toUnsignedLong(lane2) << 32
			| Short.toUnsignedLong(lane3) << 48;
	}

	/**
	 * Compares two V128 vectors for equality across 4 lanes of 32-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if equal, 0 if not equal
	 */
	public V128 equals32(V128 b) {
		return new V128(equals32Half(low, b.low), equals32Half(high, b.high));
	}

	private static long equals32Half(long a, long b) {
		int lane0 = (int)a == (int)b ? -1 : 0;
		int lane1 = (int)(a >>> 32) == (int)(b >>> 32) ? -1 : 0;
		return Integer.toUnsignedLong(lane0) | Integer.toUnsignedLong(lane1) << 32;
	}

	/**
	 * Compares two V128 vectors for inequality across 4 lanes of 32-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if not equal, 0 if equal
	 */
	public V128 notEquals32(V128 b) {
		return new V128(notEquals32Half(low, b.low), notEquals32Half(high, b.high));
	}

	private static long notEquals32Half(long a, long b) {
		int lane0 = (int)a != (int)b ? -1 : 0;
		int lane1 = (int)(a >>> 32) != (int)(b >>> 32) ? -1 : 0;
		return Integer.toUnsignedLong(lane0) | Integer.toUnsignedLong(lane1) << 32;
	}

	/**
	 * Performs signed less-than comparison across 4 lanes of 32-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than, 0 otherwise
	 */
	public V128 lessThanSigned32(V128 b) {
		return new V128(lessThanSigned32Half(low, b.low), lessThanSigned32Half(high, b.high));
	}

	private static long lessThanSigned32Half(long a, long b) {
		int lane0 = (int)a < (int)b ? -1 : 0;
		int lane1 = (int)(a >>> 32) < (int)(b >>> 32) ? -1 : 0;
		return Integer.toUnsignedLong(lane0) | Integer.toUnsignedLong(lane1) << 32;
	}

	/**
	 * Performs unsigned less-than comparison across 4 lanes of 32-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than, 0 otherwise
	 */
	public V128 lessThanUnsigned32(V128 b) {
		return new V128(lessThanUnsigned32Half(low, b.low), lessThanUnsigned32Half(high, b.high));
	}

	private static long lessThanUnsigned32Half(long a, long b) {
		int lane0 = Integer.compareUnsigned((int)a, (int)b) < 0 ? -1 : 0;
		int lane1 = Integer.compareUnsigned((int)(a >>> 32), (int)(b >>> 32)) < 0 ? -1 : 0;
		return Integer.toUnsignedLong(lane0) | Integer.toUnsignedLong(lane1) << 32;
	}

	/**
	 * Performs signed greater-than comparison across 4 lanes of 32-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than, 0 otherwise
	 */
	public V128 greaterThanSigned32(V128 b) {
		return new V128(greaterThanSigned32Half(low, b.low), greaterThanSigned32Half(high, b.high));
	}

	private static long greaterThanSigned32Half(long a, long b) {
		int lane0 = (int)a > (int)b ? -1 : 0;
		int lane1 = (int)(a >>> 32) > (int)(b >>> 32) ? -1 : 0;
		return Integer.toUnsignedLong(lane0) | Integer.toUnsignedLong(lane1) << 32;
	}

	/**
	 * Performs unsigned greater-than comparison across 4 lanes of 32-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than, 0 otherwise
	 */
	public V128 greaterThanUnsigned32(V128 b) {
		return new V128(greaterThanUnsigned32Half(low, b.low), greaterThanUnsigned32Half(high, b.high));
	}

	private static long greaterThanUnsigned32Half(long a, long b) {
		int lane0 = Integer.compareUnsigned((int)a, (int)b) > 0 ? -1 : 0;
		int lane1 = Integer.compareUnsigned((int)(a >>> 32), (int)(b >>> 32)) > 0 ? -1 : 0;
		return Integer.toUnsignedLong(lane0) | Integer.toUnsignedLong(lane1) << 32;
	}

	/**
	 * Performs signed less-than-or-equal comparison across 4 lanes of 32-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than or equal, 0
	 *         otherwise
	 */
	public V128 lessThanOrEqualSigned32(V128 b) {
		return new V128(lessThanOrEqualSigned32Half(low, b.low), lessThanOrEqualSigned32Half(high, b.high));
	}

	private static long lessThanOrEqualSigned32Half(long a, long b) {
		int lane0 = (int)a <= (int)b ? -1 : 0;
		int lane1 = (int)(a >>> 32) <= (int)(b >>> 32) ? -1 : 0;
		return Integer.toUnsignedLong(lane0) | Integer.toUnsignedLong(lane1) << 32;
	}

	/**
	 * Performs unsigned less-than-or-equal comparison across 4 lanes of 32-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than or equal, 0
	 *         otherwise
	 */
	public V128 lessThanOrEqualUnsigned32(V128 b) {
		return new V128(lessThanOrEqualUnsigned32Half(low, b.low), lessThanOrEqualUnsigned32Half(high, b.high));
	}

	private static long lessThanOrEqualUnsigned32Half(long a, long b) {
		int lane0 = Integer.compareUnsigned((int)a, (int)b) <= 0 ? -1 : 0;
		int lane1 = Integer.compareUnsigned((int)(a >>> 32), (int)(b >>> 32)) <= 0 ? -1 : 0;
		return Integer.toUnsignedLong(lane0) | Integer.toUnsignedLong(lane1) << 32;
	}

	/**
	 * Performs signed greater-than-or-equal comparison across 4 lanes of 32-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than or equal, 0
	 *         otherwise
	 */
	public V128 greaterThanOrEqualSigned32(V128 b) {
		return new V128(greaterThanOrEqualSigned32Half(low, b.low), greaterThanOrEqualSigned32Half(high, b.high));
	}

	private static long greaterThanOrEqualSigned32Half(long a, long b) {
		int lane0 = (int)a >= (int)b ? -1 : 0;
		int lane1 = (int)(a >>> 32) >= (int)(b >>> 32) ? -1 : 0;
		return Integer.toUnsignedLong(lane0) | Integer.toUnsignedLong(lane1) << 32;
	}

	/**
	 * Performs unsigned greater-than-or-equal comparison across 4 lanes of 32-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than or equal, 0
	 *         otherwise
	 */
	public V128 greaterThanOrEqualUnsigned32(V128 b) {
		return new V128(greaterThanOrEqualUnsigned32Half(low, b.low), greaterThanOrEqualUnsigned32Half(high, b.high));
	}

	private static long greaterThanOrEqualUnsigned32Half(long a, long b) {
		int lane0 = Integer.compareUnsigned((int)a, (int)b) >= 0 ? -1 : 0;
		int lane1 = Integer.compareUnsigned((int)(a >>> 32), (int)(b >>> 32)) >= 0 ? -1 : 0;
		return Integer.toUnsignedLong(lane0) | Integer.toUnsignedLong(lane1) << 32;
	}

	/**
	 * Compares two V128 vectors for equality across 2 lanes of 64-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if equal, 0 if not equal
	 */
	public V128 equals64(V128 b) {
		return new V128(low == b.low ? -1L : 0L, high == b.high ? -1L : 0L);
	}

	/**
	 * Compares two V128 vectors for inequality across 2 lanes of 64-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if not equal, 0 if equal
	 */
	public V128 notEquals64(V128 b) {
		return new V128(low != b.low ? -1L : 0L, high != b.high ? -1L : 0L);
	}

	/**
	 * Performs signed less-than comparison across 2 lanes of 64-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than, 0 otherwise
	 */
	public V128 lessThanSigned64(V128 b) {
		return new V128(low < b.low ? -1L : 0L, high < b.high ? -1L : 0L);
	}

	/**
	 * Performs unsigned less-than comparison across 2 lanes of 64-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than, 0 otherwise
	 */
	public V128 lessThanUnsigned64(V128 b) {
		return new V128(
			Long.compareUnsigned(low, b.low) < 0 ? -1L : 0L,
			Long.compareUnsigned(high, b.high) < 0 ? -1L : 0L
		);
	}

	/**
	 * Performs signed greater-than comparison across 2 lanes of 64-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than, 0 otherwise
	 */
	public V128 greaterThanSigned64(V128 b) {
		return new V128(low > b.low ? -1L : 0L, high > b.high ? -1L : 0L);
	}

	/**
	 * Performs unsigned greater-than comparison across 2 lanes of 64-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than, 0 otherwise
	 */
	public V128 greaterThanUnsigned64(V128 b) {
		return new V128(
			Long.compareUnsigned(low, b.low) > 0 ? -1L : 0L,
			Long.compareUnsigned(high, b.high) > 0 ? -1L : 0L
		);
	}

	/**
	 * Performs signed less-than-or-equal comparison across 2 lanes of 64-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than or equal, 0
	 *         otherwise
	 */
	public V128 lessThanOrEqualSigned64(V128 b) {
		return new V128(low <= b.low ? -1L : 0L, high <= b.high ? -1L : 0L);
	}

	/**
	 * Performs unsigned less-than-or-equal comparison across 2 lanes of 64-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than or equal, 0
	 *         otherwise
	 */
	public V128 lessThanOrEqualUnsigned64(V128 b) {
		return new V128(
			Long.compareUnsigned(low, b.low) <= 0 ? -1L : 0L,
			Long.compareUnsigned(high, b.high) <= 0 ? -1L : 0L
		);
	}

	/**
	 * Performs signed greater-than-or-equal comparison across 2 lanes of 64-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than or equal, 0
	 *         otherwise
	 */
	public V128 greaterThanOrEqualSigned64(V128 b) {
		return new V128(low >= b.low ? -1L : 0L, high >= b.high ? -1L : 0L);
	}

	/**
	 * Performs unsigned greater-than-or-equal comparison across 2 lanes of 64-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than or equal, 0
	 *         otherwise
	 */
	public V128 greaterThanOrEqualUnsigned64(V128 b) {
		return new V128(
			Long.compareUnsigned(low, b.low) >= 0 ? -1L : 0L,
			Long.compareUnsigned(high, b.high) >= 0 ? -1L : 0L
		);
	}

	/**
	 * Computes the absolute value of each 8-bit lane in the V128 vector.
	 * 
	 * @return A new V128 with absolute values in each of the 16 lanes
	 */
	public V128 abs8() {
		return new V128(
			absLanes(low, 8, 0x0101010101010101L, 0x7f7f7f7f7f7f7f7fL),
			absLanes(high, 8, 0x0101010101010101L, 0x7f7f7f7f7f7f7f7fL)
		);
	}

	/**
	 * Negates each 8-bit lane in the V128 vector.
	 * 
	 * @return A new V128 with negated values in each of the 16 lanes
	 */
	public V128 neg8() {
		return new V128(subtractLanes(0, low, 0x7f7f7f7f7f7f7f7fL), subtractLanes(0, high, 0x7f7f7f7f7f7f7f7fL));
	}

	/**
	 * Computes the absolute value of each 16-bit lane in the V128 vector.
	 * 
	 * @return A new V128 with absolute values in each of the 8 lanes
	 */
	public V128 abs16() {
		return new V128(
			absLanes(low, 16, 0x0001000100010001L, 0x7fff7fff7fff7fffL),
			absLanes(high, 16, 0x0001000100010001L, 0x7fff7fff7fff7fffL)
		);
	}

	/**
	 * Negates each 16-bit lane in the V128 vector.
	 * 
	 * @return A new V128 with negated values in each of the 8 lanes
	 */
	public V128 neg16() {
		return new V128(subtractLanes(0, low, 0x7fff7fff7fff7fffL), subtractLanes(0, high, 0x7fff7fff7fff7fffL));
	}

	/**
	 * Computes the absolute value of each 32-bit lane in the V128 vector.
	 * 
	 * @return A new V128 with absolute values in each of the 4 lanes
	 */
	public V128 abs32() {
		return new V128(
			Integer.toUnsignedLong(Math.abs((int)low)) | ((long)Math.abs((int)(low >>> 32)) << 32),
			Integer.toUnsignedLong(Math.abs((int)high)) | ((long)Math.abs((int)(high >>> 32)) << 32)
		);
	}

	/**
	 * Negates each 32-bit lane in the V128 vector.
	 * 
	 * @return A new V128 with negated values in each of the 4 lanes
	 */
	public V128 neg32() {
		return new V128(subtract32Lanes(0, low), subtract32Lanes(0, high));
	}

	// Lane size 64 (i64x2)

	/**
	 * Computes the absolute value of each 64-bit lane in the V128 vector.
	 * 
	 * @return A new V128 with absolute values in each of the 2 lanes
	 */
	public V128 abs64() {
		return new V128(Math.abs(low), Math.abs(high));
	}

	/**
	 * Negates each 64-bit lane in the V128 vector.
	 * 
	 * @return A new V128 with negated values in each of the 2 lanes
	 */
	public V128 neg64() {
		return new V128(-low, -high);
	}

	private static long add32Lanes(long a, long b) {
		return ((a + b) & 0xffffffffL) + ((a & 0xffffffff00000000L) + (b & 0xffffffff00000000L));
	}

	private static long subtract32Lanes(long a, long b) {
		return ((a - b) & 0xffffffffL) | ((a & 0xffffffff00000000L) - (b & 0xffffffff00000000L));
	}

	// Clear each lane's top bit so addition cannot carry into the next lane.
	// Restore the top bits modulo the lane width with XOR.
	private static long addLanes(long a, long b, long lowerBits) {
		return ((a & lowerBits) + (b & lowerBits)) ^ ((a ^ b) & ~lowerBits);
	}

	// Set each minuend lane's top bit to stop borrows crossing lane boundaries.
	private static long subtractLanes(long a, long b, long lowerBits) {
		return ((a | ~lowerBits) - (b & lowerBits)) ^ ((a ^ ~b) & ~lowerBits);
	}

	/**
	 * Adds two V128 vectors across 16 lanes of 8-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to add
	 * @return A new V128 with the sum of corresponding lanes
	 */
	public V128 add8(V128 b) {
		return new V128(addLanes(low, b.low, 0x7f7f7f7f7f7f7f7fL), addLanes(high, b.high, 0x7f7f7f7f7f7f7f7fL));
	}

	/**
	 * Subtracts the second V128 vector from the first across 16 lanes of 8-bit
	 * values.
	 * 
	 * @param b
	 *            The V128 vector to subtract
	 * @return A new V128 with the difference of corresponding lanes
	 */
	public V128 sub8(V128 b) {
		return new V128(
			subtractLanes(low, b.low, 0x7f7f7f7f7f7f7f7fL),
			subtractLanes(high, b.high, 0x7f7f7f7f7f7f7f7fL)
		);
	}

	/**
	 * Adds two V128 vectors across 8 lanes of 16-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to add
	 * @return A new V128 with the sum of corresponding lanes
	 */
	public V128 add16(V128 b) {
		return new V128(addLanes(low, b.low, 0x7fff7fff7fff7fffL), addLanes(high, b.high, 0x7fff7fff7fff7fffL));
	}

	/**
	 * Subtracts the second V128 vector from the first across 8 lanes of 16-bit
	 * values.
	 * 
	 * @param b
	 *            The V128 vector to subtract
	 * @return A new V128 with the difference of corresponding lanes
	 */
	public V128 sub16(V128 b) {
		return new V128(
			subtractLanes(low, b.low, 0x7fff7fff7fff7fffL),
			subtractLanes(high, b.high, 0x7fff7fff7fff7fffL)
		);
	}

	/**
	 * Adds two V128 vectors across 4 lanes of 32-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to add
	 * @return A new V128 with the sum of corresponding lanes
	 */
	public V128 add32(V128 b) {
		return new V128(add32Lanes(low, b.low), add32Lanes(high, b.high));
	}

	/**
	 * Subtracts the second V128 vector from the first across 4 lanes of 32-bit
	 * values.
	 * 
	 * @param b
	 *            The V128 vector to subtract
	 * @return A new V128 with the difference of corresponding lanes
	 */
	public V128 sub32(V128 b) {
		return new V128(subtract32Lanes(low, b.low), subtract32Lanes(high, b.high));
	}

	/**
	 * Adds two V128 vectors across 2 lanes of 64-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to add
	 * @return A new V128 with the sum of corresponding lanes
	 */
	public V128 add64(V128 b) {
		return new V128(low + b.low, high + b.high);
	}

	/**
	 * Subtracts the second V128 vector from the first across 2 lanes of 64-bit
	 * values.
	 * 
	 * @param b
	 *            The V128 vector to subtract
	 * @return A new V128 with the difference of corresponding lanes
	 */
	public V128 sub64(V128 b) {
		return new V128(low - b.low, high - b.high);
	}

	/**
	 * Shifts each 8-bit lane left by the specified amount.
	 * 
	 * @param amount
	 *            The number of bits to shift (0-7)
	 * @return A new V128 with each lane shifted left
	 */
	public V128 shl8(int amount) {
		int shift = amount & 7;
		long mask = 0x0101010101010101L * ((0xffL << shift) & 0xffL);
		return new V128((low << shift) & mask, (high << shift) & mask);
	}

	/**
	 * Shifts each 8-bit lane right by the specified amount (unsigned).
	 * 
	 * @param amount
	 *            The number of bits to shift (0-7)
	 * @return A new V128 with each lane shifted right (zero-filled)
	 */
	public V128 shrU8(int amount) {
		int shift = amount & 7;
		long mask = 0x0101010101010101L * (0xffL >>> shift);
		return new V128((low >>> shift) & mask, (high >>> shift) & mask);
	}

	/**
	 * Shifts each 8-bit lane right by the specified amount (signed).
	 * 
	 * @param amount
	 *            The number of bits to shift (0-7)
	 * @return A new V128 with each lane shifted right (sign-extended)
	 */
	public V128 shrS8(int amount) {
		int shift = amount & 7;
		long mask = 0x0101010101010101L * (0xffL >>> shift);
		return new V128(
			shiftRightSigned(low, shift, mask, 8, 0x0101010101010101L),
			shiftRightSigned(high, shift, mask, 8, 0x0101010101010101L)
		);
	}

	/**
	 * Shifts each 16-bit lane left by the specified amount.
	 * 
	 * @param amount
	 *            The number of bits to shift (0-15)
	 * @return A new V128 with each lane shifted left
	 */
	public V128 shl16(int amount) {
		int shift = amount & 15;
		long mask = 0x0001000100010001L * ((0xffffL << shift) & 0xffffL);
		return new V128((low << shift) & mask, (high << shift) & mask);
	}

	/**
	 * Shifts each 16-bit lane right by the specified amount (unsigned).
	 * 
	 * @param amount
	 *            The number of bits to shift (0-15)
	 * @return A new V128 with each lane shifted right (zero-filled)
	 */
	public V128 shrU16(int amount) {
		int shift = amount & 15;
		long mask = 0x0001000100010001L * (0xffffL >>> shift);
		return new V128((low >>> shift) & mask, (high >>> shift) & mask);
	}

	/**
	 * Shifts each 16-bit lane right by the specified amount (signed).
	 * 
	 * @param amount
	 *            The number of bits to shift (0-15)
	 * @return A new V128 with each lane shifted right (sign-extended)
	 */
	public V128 shrS16(int amount) {
		int shift = amount & 15;
		long mask = 0x0001000100010001L * (0xffffL >>> shift);
		return new V128(
			shiftRightSigned(low, shift, mask, 16, 0x0001000100010001L),
			shiftRightSigned(high, shift, mask, 16, 0x0001000100010001L)
		);
	}

	/**
	 * Shifts each 32-bit lane left by the specified amount.
	 * 
	 * @param amount
	 *            The number of bits to shift (0-31)
	 * @return A new V128 with each lane shifted left
	 */
	public V128 shl32(int amount) {
		int shift = amount & 31;
		long mask = 0x0000000100000001L * ((0xffffffffL << shift) & 0xffffffffL);
		return new V128((low << shift) & mask, (high << shift) & mask);
	}

	/**
	 * Shifts each 32-bit lane right by the specified amount (unsigned).
	 * 
	 * @param amount
	 *            The number of bits to shift (0-31)
	 * @return A new V128 with each lane shifted right (zero-filled)
	 */
	public V128 shrU32(int amount) {
		int shift = amount & 31;
		long mask = 0x0000000100000001L * (0xffffffffL >>> shift);
		return new V128((low >>> shift) & mask, (high >>> shift) & mask);
	}

	/**
	 * Shifts each 32-bit lane right by the specified amount (signed).
	 * 
	 * @param amount
	 *            The number of bits to shift (0-31)
	 * @return A new V128 with each lane shifted right (sign-extended)
	 */
	public V128 shrS32(int amount) {
		int shift = amount & 31;
		long mask = 0x0000000100000001L * (0xffffffffL >>> shift);
		return new V128(
			shiftRightSigned(low, shift, mask, 32, 0x0000000100000001L),
			shiftRightSigned(high, shift, mask, 32, 0x0000000100000001L)
		);
	}

	/**
	 * Shifts each 64-bit lane left by the specified amount.
	 * 
	 * @param amount
	 *            The number of bits to shift (0-63)
	 * @return A new V128 with each lane shifted left
	 */
	public V128 shl64(int amount) {
		return new V128(low << amount, high << amount);
	}

	/**
	 * Shifts each 64-bit lane right by the specified amount (unsigned).
	 * 
	 * @param amount
	 *            The number of bits to shift (0-63)
	 * @return A new V128 with each lane shifted right (zero-filled)
	 */
	public V128 shrU64(int amount) {
		return new V128(low >>> amount, high >>> amount);
	}

	/**
	 * Shifts each 64-bit lane right by the specified amount (signed).
	 * 
	 * @param amount
	 *            The number of bits to shift (0-63)
	 * @return A new V128 with each lane shifted right (sign-extended)
	 */
	public V128 shrS64(int amount) {
		return new V128(low >> amount, high >> amount);
	}

	/**
	 * Computes the rounded average of two V128 vectors across 16 lanes of 8-bit
	 * unsigned values. The average is calculated as (a + b + 1) / 2 for each lane.
	 * 
	 * @param b
	 *            The second V128 vector to average with
	 * @return A new V128 with the rounded average of corresponding lanes
	 */
	public V128 avgrU8(V128 b) {
		// Rounded average: (a | b) - floor((a ^ b) / 2), independently per lane.
		return new V128(
			(low | b.low) - (((low ^ b.low) >>> 1) & 0x7f7f7f7f7f7f7f7fL),
			(high | b.high) - (((high ^ b.high) >>> 1) & 0x7f7f7f7f7f7f7f7fL)
		);
	}

	/**
	 * Computes the rounded average of two V128 vectors across 8 lanes of 16-bit
	 * unsigned values. The average is calculated as (a + b + 1) / 2 for each lane.
	 * 
	 * @param b
	 *            The second V128 vector to average with
	 * @return A new V128 with the rounded average of corresponding lanes
	 */
	public V128 avgrU16(V128 b) {
		// Rounded average: (a | b) - floor((a ^ b) / 2), independently per lane.
		return new V128(
			(low | b.low) - (((low ^ b.low) >>> 1) & 0x7fff7fff7fff7fffL),
			(high | b.high) - (((high ^ b.high) >>> 1) & 0x7fff7fff7fff7fffL)
		);
	}

	/**
	 * Computes the signed minimum of two V128 vectors across 16 lanes of 8-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 with the minimum of corresponding lanes (signed)
	 */
	public V128 minS8(V128 b) {
		return new V128(minS8Half(low, b.low), minS8Half(high, b.high));
	}

	private static long minS8Half(long a, long b) {
		byte lane0 = (byte)Math.min((byte)a, (byte)b);
		byte lane1 = (byte)Math.min((byte)(a >>> 8), (byte)(b >>> 8));
		byte lane2 = (byte)Math.min((byte)(a >>> 16), (byte)(b >>> 16));
		byte lane3 = (byte)Math.min((byte)(a >>> 24), (byte)(b >>> 24));
		byte lane4 = (byte)Math.min((byte)(a >>> 32), (byte)(b >>> 32));
		byte lane5 = (byte)Math.min((byte)(a >>> 40), (byte)(b >>> 40));
		byte lane6 = (byte)Math.min((byte)(a >>> 48), (byte)(b >>> 48));
		byte lane7 = (byte)Math.min((byte)(a >>> 56), (byte)(b >>> 56));
		return Byte.toUnsignedLong(lane0) | Byte.toUnsignedLong(lane1) << 8 | Byte.toUnsignedLong(lane2) << 16
			| Byte.toUnsignedLong(lane3) << 24 | Byte.toUnsignedLong(lane4) << 32 | Byte.toUnsignedLong(lane5) << 40
			| Byte.toUnsignedLong(lane6) << 48 | Byte.toUnsignedLong(lane7) << 56;
	}

	/**
	 * Computes the unsigned minimum of two V128 vectors across 16 lanes of 8-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 with the minimum of corresponding lanes (unsigned)
	 */
	public V128 minU8(V128 b) {
		return new V128(minU8Half(low, b.low), minU8Half(high, b.high));
	}

	private static long minU8Half(long a, long b) {
		byte lane0 = (byte)Math.min(Byte.toUnsignedInt((byte)a), Byte.toUnsignedInt((byte)b));
		byte lane1 = (byte)Math.min(Byte.toUnsignedInt((byte)(a >>> 8)), Byte.toUnsignedInt((byte)(b >>> 8)));
		byte lane2 = (byte)Math.min(Byte.toUnsignedInt((byte)(a >>> 16)), Byte.toUnsignedInt((byte)(b >>> 16)));
		byte lane3 = (byte)Math.min(Byte.toUnsignedInt((byte)(a >>> 24)), Byte.toUnsignedInt((byte)(b >>> 24)));
		byte lane4 = (byte)Math.min(Byte.toUnsignedInt((byte)(a >>> 32)), Byte.toUnsignedInt((byte)(b >>> 32)));
		byte lane5 = (byte)Math.min(Byte.toUnsignedInt((byte)(a >>> 40)), Byte.toUnsignedInt((byte)(b >>> 40)));
		byte lane6 = (byte)Math.min(Byte.toUnsignedInt((byte)(a >>> 48)), Byte.toUnsignedInt((byte)(b >>> 48)));
		byte lane7 = (byte)Math.min(Byte.toUnsignedInt((byte)(a >>> 56)), Byte.toUnsignedInt((byte)(b >>> 56)));
		return Byte.toUnsignedLong(lane0) | Byte.toUnsignedLong(lane1) << 8 | Byte.toUnsignedLong(lane2) << 16
			| Byte.toUnsignedLong(lane3) << 24 | Byte.toUnsignedLong(lane4) << 32 | Byte.toUnsignedLong(lane5) << 40
			| Byte.toUnsignedLong(lane6) << 48 | Byte.toUnsignedLong(lane7) << 56;
	}

	/**
	 * Computes the signed maximum of two V128 vectors across 16 lanes of 8-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 with the maximum of corresponding lanes (signed)
	 */
	public V128 maxS8(V128 b) {
		return new V128(maxS8Half(low, b.low), maxS8Half(high, b.high));
	}

	private static long maxS8Half(long a, long b) {
		byte lane0 = (byte)Math.max((byte)a, (byte)b);
		byte lane1 = (byte)Math.max((byte)(a >>> 8), (byte)(b >>> 8));
		byte lane2 = (byte)Math.max((byte)(a >>> 16), (byte)(b >>> 16));
		byte lane3 = (byte)Math.max((byte)(a >>> 24), (byte)(b >>> 24));
		byte lane4 = (byte)Math.max((byte)(a >>> 32), (byte)(b >>> 32));
		byte lane5 = (byte)Math.max((byte)(a >>> 40), (byte)(b >>> 40));
		byte lane6 = (byte)Math.max((byte)(a >>> 48), (byte)(b >>> 48));
		byte lane7 = (byte)Math.max((byte)(a >>> 56), (byte)(b >>> 56));
		return Byte.toUnsignedLong(lane0) | Byte.toUnsignedLong(lane1) << 8 | Byte.toUnsignedLong(lane2) << 16
			| Byte.toUnsignedLong(lane3) << 24 | Byte.toUnsignedLong(lane4) << 32 | Byte.toUnsignedLong(lane5) << 40
			| Byte.toUnsignedLong(lane6) << 48 | Byte.toUnsignedLong(lane7) << 56;
	}

	/**
	 * Computes the unsigned maximum of two V128 vectors across 16 lanes of 8-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 with the maximum of corresponding lanes (unsigned)
	 */
	public V128 maxU8(V128 b) {
		return new V128(maxU8Half(low, b.low), maxU8Half(high, b.high));
	}

	private static long maxU8Half(long a, long b) {
		byte lane0 = (byte)Math.max(Byte.toUnsignedInt((byte)a), Byte.toUnsignedInt((byte)b));
		byte lane1 = (byte)Math.max(Byte.toUnsignedInt((byte)(a >>> 8)), Byte.toUnsignedInt((byte)(b >>> 8)));
		byte lane2 = (byte)Math.max(Byte.toUnsignedInt((byte)(a >>> 16)), Byte.toUnsignedInt((byte)(b >>> 16)));
		byte lane3 = (byte)Math.max(Byte.toUnsignedInt((byte)(a >>> 24)), Byte.toUnsignedInt((byte)(b >>> 24)));
		byte lane4 = (byte)Math.max(Byte.toUnsignedInt((byte)(a >>> 32)), Byte.toUnsignedInt((byte)(b >>> 32)));
		byte lane5 = (byte)Math.max(Byte.toUnsignedInt((byte)(a >>> 40)), Byte.toUnsignedInt((byte)(b >>> 40)));
		byte lane6 = (byte)Math.max(Byte.toUnsignedInt((byte)(a >>> 48)), Byte.toUnsignedInt((byte)(b >>> 48)));
		byte lane7 = (byte)Math.max(Byte.toUnsignedInt((byte)(a >>> 56)), Byte.toUnsignedInt((byte)(b >>> 56)));
		return Byte.toUnsignedLong(lane0) | Byte.toUnsignedLong(lane1) << 8 | Byte.toUnsignedLong(lane2) << 16
			| Byte.toUnsignedLong(lane3) << 24 | Byte.toUnsignedLong(lane4) << 32 | Byte.toUnsignedLong(lane5) << 40
			| Byte.toUnsignedLong(lane6) << 48 | Byte.toUnsignedLong(lane7) << 56;
	}

	/**
	 * Computes the signed minimum of two V128 vectors across 8 lanes of 16-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 with the minimum of corresponding lanes (signed)
	 */
	public V128 minS16(V128 b) {
		return new V128(minS16Half(low, b.low), minS16Half(high, b.high));
	}

	private static long minS16Half(long a, long b) {
		short lane0 = (short)Math.min((short)a, (short)b);
		short lane1 = (short)Math.min((short)(a >>> 16), (short)(b >>> 16));
		short lane2 = (short)Math.min((short)(a >>> 32), (short)(b >>> 32));
		short lane3 = (short)Math.min((short)(a >>> 48), (short)(b >>> 48));
		return Short.toUnsignedLong(lane0) | Short.toUnsignedLong(lane1) << 16 | Short.toUnsignedLong(lane2) << 32
			| Short.toUnsignedLong(lane3) << 48;
	}

	/**
	 * Computes the unsigned minimum of two V128 vectors across 8 lanes of 16-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 with the minimum of corresponding lanes (unsigned)
	 */
	public V128 minU16(V128 b) {
		return new V128(minU16Half(low, b.low), minU16Half(high, b.high));
	}

	private static long minU16Half(long a, long b) {
		short lane0 = (short)Math.min(Short.toUnsignedInt((short)a), Short.toUnsignedInt((short)b));
		short lane1 = (short)Math.min(Short.toUnsignedInt((short)(a >>> 16)), Short.toUnsignedInt((short)(b >>> 16)));
		short lane2 = (short)Math.min(Short.toUnsignedInt((short)(a >>> 32)), Short.toUnsignedInt((short)(b >>> 32)));
		short lane3 = (short)Math.min(Short.toUnsignedInt((short)(a >>> 48)), Short.toUnsignedInt((short)(b >>> 48)));
		return Short.toUnsignedLong(lane0) | Short.toUnsignedLong(lane1) << 16 | Short.toUnsignedLong(lane2) << 32
			| Short.toUnsignedLong(lane3) << 48;
	}

	/**
	 * Computes the signed maximum of two V128 vectors across 8 lanes of 16-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 with the maximum of corresponding lanes (signed)
	 */
	public V128 maxS16(V128 b) {
		return new V128(maxS16Half(low, b.low), maxS16Half(high, b.high));
	}

	private static long maxS16Half(long a, long b) {
		short lane0 = (short)Math.max((short)a, (short)b);
		short lane1 = (short)Math.max((short)(a >>> 16), (short)(b >>> 16));
		short lane2 = (short)Math.max((short)(a >>> 32), (short)(b >>> 32));
		short lane3 = (short)Math.max((short)(a >>> 48), (short)(b >>> 48));
		return Short.toUnsignedLong(lane0) | Short.toUnsignedLong(lane1) << 16 | Short.toUnsignedLong(lane2) << 32
			| Short.toUnsignedLong(lane3) << 48;
	}

	/**
	 * Computes the unsigned maximum of two V128 vectors across 8 lanes of 16-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 with the maximum of corresponding lanes (unsigned)
	 */
	public V128 maxU16(V128 b) {
		return new V128(maxU16Half(low, b.low), maxU16Half(high, b.high));
	}

	private static long maxU16Half(long a, long b) {
		short lane0 = (short)Math.max(Short.toUnsignedInt((short)a), Short.toUnsignedInt((short)b));
		short lane1 = (short)Math.max(Short.toUnsignedInt((short)(a >>> 16)), Short.toUnsignedInt((short)(b >>> 16)));
		short lane2 = (short)Math.max(Short.toUnsignedInt((short)(a >>> 32)), Short.toUnsignedInt((short)(b >>> 32)));
		short lane3 = (short)Math.max(Short.toUnsignedInt((short)(a >>> 48)), Short.toUnsignedInt((short)(b >>> 48)));
		return Short.toUnsignedLong(lane0) | Short.toUnsignedLong(lane1) << 16 | Short.toUnsignedLong(lane2) << 32
			| Short.toUnsignedLong(lane3) << 48;
	}

	/**
	 * Computes the signed minimum of two V128 vectors across 4 lanes of 32-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 with the minimum of corresponding lanes (signed)
	 */
	public V128 minS32(V128 b) {
		return new V128(minS32Half(low, b.low), minS32Half(high, b.high));
	}

	private static long minS32Half(long a, long b) {
		int lane0 = Math.min((int)a, (int)b);
		int lane1 = Math.min((int)(a >>> 32), (int)(b >>> 32));
		return Integer.toUnsignedLong(lane0) | Integer.toUnsignedLong(lane1) << 32;
	}

	/**
	 * Computes the unsigned minimum of two V128 vectors across 4 lanes of 32-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 with the minimum of corresponding lanes (unsigned)
	 */
	public V128 minU32(V128 b) {
		return new V128(minU32Half(low, b.low), minU32Half(high, b.high));
	}

	private static long minU32Half(long a, long b) {
		int lane0 = Util.minU32((int)a, (int)b);
		int lane1 = Util.minU32((int)(a >>> 32), (int)(b >>> 32));
		return Integer.toUnsignedLong(lane0) | Integer.toUnsignedLong(lane1) << 32;
	}

	/**
	 * Computes the signed maximum of two V128 vectors across 4 lanes of 32-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 with the maximum of corresponding lanes (signed)
	 */
	public V128 maxS32(V128 b) {
		return new V128(maxS32Half(low, b.low), maxS32Half(high, b.high));
	}

	private static long maxS32Half(long a, long b) {
		int lane0 = Math.max((int)a, (int)b);
		int lane1 = Math.max((int)(a >>> 32), (int)(b >>> 32));
		return Integer.toUnsignedLong(lane0) | Integer.toUnsignedLong(lane1) << 32;
	}

	/**
	 * Computes the unsigned maximum of two V128 vectors across 4 lanes of 32-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 with the maximum of corresponding lanes (unsigned)
	 */
	public V128 maxU32(V128 b) {
		return new V128(maxU32Half(low, b.low), maxU32Half(high, b.high));
	}

	private static long maxU32Half(long a, long b) {
		int lane0 = Util.maxU32((int)a, (int)b);
		int lane1 = Util.maxU32((int)(a >>> 32), (int)(b >>> 32));
		return Integer.toUnsignedLong(lane0) | Integer.toUnsignedLong(lane1) << 32;
	}

	/**
	 * Performs saturating signed addition of two V128 vectors across 16 lanes of
	 * 8-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to add
	 * @return A new V128 with saturated sums of corresponding lanes (signed)
	 */
	public V128 addSatS8(V128 b) {
		return new V128(addSatS8Half(low, b.low), addSatS8Half(high, b.high));
	}

	private static long addSatS8Half(long a, long b) {
		byte lane0 = Util.addSatS8((byte)a, (byte)b);
		byte lane1 = Util.addSatS8((byte)(a >>> 8), (byte)(b >>> 8));
		byte lane2 = Util.addSatS8((byte)(a >>> 16), (byte)(b >>> 16));
		byte lane3 = Util.addSatS8((byte)(a >>> 24), (byte)(b >>> 24));
		byte lane4 = Util.addSatS8((byte)(a >>> 32), (byte)(b >>> 32));
		byte lane5 = Util.addSatS8((byte)(a >>> 40), (byte)(b >>> 40));
		byte lane6 = Util.addSatS8((byte)(a >>> 48), (byte)(b >>> 48));
		byte lane7 = Util.addSatS8((byte)(a >>> 56), (byte)(b >>> 56));
		return Byte.toUnsignedLong(lane0) | Byte.toUnsignedLong(lane1) << 8 | Byte.toUnsignedLong(lane2) << 16
			| Byte.toUnsignedLong(lane3) << 24 | Byte.toUnsignedLong(lane4) << 32 | Byte.toUnsignedLong(lane5) << 40
			| Byte.toUnsignedLong(lane6) << 48 | Byte.toUnsignedLong(lane7) << 56;
	}

	/**
	 * Performs saturating unsigned addition of two V128 vectors across 16 lanes of
	 * 8-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to add
	 * @return A new V128 with saturated sums of corresponding lanes (unsigned)
	 */
	public V128 addSatU8(V128 b) {
		return new V128(addSatU8Half(low, b.low), addSatU8Half(high, b.high));
	}

	private static long addSatU8Half(long a, long b) {
		byte lane0 = Util.addSatU8((byte)a, (byte)b);
		byte lane1 = Util.addSatU8((byte)(a >>> 8), (byte)(b >>> 8));
		byte lane2 = Util.addSatU8((byte)(a >>> 16), (byte)(b >>> 16));
		byte lane3 = Util.addSatU8((byte)(a >>> 24), (byte)(b >>> 24));
		byte lane4 = Util.addSatU8((byte)(a >>> 32), (byte)(b >>> 32));
		byte lane5 = Util.addSatU8((byte)(a >>> 40), (byte)(b >>> 40));
		byte lane6 = Util.addSatU8((byte)(a >>> 48), (byte)(b >>> 48));
		byte lane7 = Util.addSatU8((byte)(a >>> 56), (byte)(b >>> 56));
		return Byte.toUnsignedLong(lane0) | Byte.toUnsignedLong(lane1) << 8 | Byte.toUnsignedLong(lane2) << 16
			| Byte.toUnsignedLong(lane3) << 24 | Byte.toUnsignedLong(lane4) << 32 | Byte.toUnsignedLong(lane5) << 40
			| Byte.toUnsignedLong(lane6) << 48 | Byte.toUnsignedLong(lane7) << 56;
	}

	/**
	 * Performs saturating signed subtraction of two V128 vectors across 16 lanes of
	 * 8-bit values.
	 * 
	 * @param b
	 *            The V128 vector to subtract
	 * @return A new V128 with saturated differences of corresponding lanes (signed)
	 */
	public V128 subSatS8(V128 b) {
		return new V128(subSatS8Half(low, b.low), subSatS8Half(high, b.high));
	}

	private static long subSatS8Half(long a, long b) {
		byte lane0 = Util.subSatS8((byte)a, (byte)b);
		byte lane1 = Util.subSatS8((byte)(a >>> 8), (byte)(b >>> 8));
		byte lane2 = Util.subSatS8((byte)(a >>> 16), (byte)(b >>> 16));
		byte lane3 = Util.subSatS8((byte)(a >>> 24), (byte)(b >>> 24));
		byte lane4 = Util.subSatS8((byte)(a >>> 32), (byte)(b >>> 32));
		byte lane5 = Util.subSatS8((byte)(a >>> 40), (byte)(b >>> 40));
		byte lane6 = Util.subSatS8((byte)(a >>> 48), (byte)(b >>> 48));
		byte lane7 = Util.subSatS8((byte)(a >>> 56), (byte)(b >>> 56));
		return Byte.toUnsignedLong(lane0) | Byte.toUnsignedLong(lane1) << 8 | Byte.toUnsignedLong(lane2) << 16
			| Byte.toUnsignedLong(lane3) << 24 | Byte.toUnsignedLong(lane4) << 32 | Byte.toUnsignedLong(lane5) << 40
			| Byte.toUnsignedLong(lane6) << 48 | Byte.toUnsignedLong(lane7) << 56;
	}

	/**
	 * Performs saturating unsigned subtraction of two V128 vectors across 16 lanes
	 * of 8-bit values.
	 * 
	 * @param b
	 *            The V128 vector to subtract
	 * @return A new V128 with saturated differences of corresponding lanes
	 *         (unsigned)
	 */
	public V128 subSatU8(V128 b) {
		return new V128(subSatU8Half(low, b.low), subSatU8Half(high, b.high));
	}

	private static long subSatU8Half(long a, long b) {
		byte lane0 = Util.subSatU8((byte)a, (byte)b);
		byte lane1 = Util.subSatU8((byte)(a >>> 8), (byte)(b >>> 8));
		byte lane2 = Util.subSatU8((byte)(a >>> 16), (byte)(b >>> 16));
		byte lane3 = Util.subSatU8((byte)(a >>> 24), (byte)(b >>> 24));
		byte lane4 = Util.subSatU8((byte)(a >>> 32), (byte)(b >>> 32));
		byte lane5 = Util.subSatU8((byte)(a >>> 40), (byte)(b >>> 40));
		byte lane6 = Util.subSatU8((byte)(a >>> 48), (byte)(b >>> 48));
		byte lane7 = Util.subSatU8((byte)(a >>> 56), (byte)(b >>> 56));
		return Byte.toUnsignedLong(lane0) | Byte.toUnsignedLong(lane1) << 8 | Byte.toUnsignedLong(lane2) << 16
			| Byte.toUnsignedLong(lane3) << 24 | Byte.toUnsignedLong(lane4) << 32 | Byte.toUnsignedLong(lane5) << 40
			| Byte.toUnsignedLong(lane6) << 48 | Byte.toUnsignedLong(lane7) << 56;
	}

	/**
	 * Performs saturating signed addition of two V128 vectors across 8 lanes of
	 * 16-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to add
	 * @return A new V128 with saturated sums of corresponding lanes (signed)
	 */
	public V128 addSatS16(V128 b) {
		return new V128(addSatS16Half(low, b.low), addSatS16Half(high, b.high));
	}

	private static long addSatS16Half(long a, long b) {
		short lane0 = Util.addSatS16((short)a, (short)b);
		short lane1 = Util.addSatS16((short)(a >>> 16), (short)(b >>> 16));
		short lane2 = Util.addSatS16((short)(a >>> 32), (short)(b >>> 32));
		short lane3 = Util.addSatS16((short)(a >>> 48), (short)(b >>> 48));
		return Short.toUnsignedLong(lane0) | Short.toUnsignedLong(lane1) << 16 | Short.toUnsignedLong(lane2) << 32
			| Short.toUnsignedLong(lane3) << 48;
	}

	/**
	 * Performs saturating unsigned addition of two V128 vectors across 8 lanes of
	 * 16-bit values.
	 * 
	 * @param b
	 *            The second V128 vector to add
	 * @return A new V128 with saturated sums of corresponding lanes (unsigned)
	 */
	public V128 addSatU16(V128 b) {
		return new V128(addSatU16Half(low, b.low), addSatU16Half(high, b.high));
	}

	private static long addSatU16Half(long a, long b) {
		short lane0 = Util.addSatU16((short)a, (short)b);
		short lane1 = Util.addSatU16((short)(a >>> 16), (short)(b >>> 16));
		short lane2 = Util.addSatU16((short)(a >>> 32), (short)(b >>> 32));
		short lane3 = Util.addSatU16((short)(a >>> 48), (short)(b >>> 48));
		return Short.toUnsignedLong(lane0) | Short.toUnsignedLong(lane1) << 16 | Short.toUnsignedLong(lane2) << 32
			| Short.toUnsignedLong(lane3) << 48;
	}

	/**
	 * Performs saturating signed subtraction of two V128 vectors across 8 lanes of
	 * 16-bit values.
	 * 
	 * @param b
	 *            The V128 vector to subtract
	 * @return A new V128 with saturated differences of corresponding lanes (signed)
	 */
	public V128 subSatS16(V128 b) {
		return new V128(subSatS16Half(low, b.low), subSatS16Half(high, b.high));
	}

	private static long subSatS16Half(long a, long b) {
		short lane0 = Util.subSatS16((short)a, (short)b);
		short lane1 = Util.subSatS16((short)(a >>> 16), (short)(b >>> 16));
		short lane2 = Util.subSatS16((short)(a >>> 32), (short)(b >>> 32));
		short lane3 = Util.subSatS16((short)(a >>> 48), (short)(b >>> 48));
		return Short.toUnsignedLong(lane0) | Short.toUnsignedLong(lane1) << 16 | Short.toUnsignedLong(lane2) << 32
			| Short.toUnsignedLong(lane3) << 48;
	}

	/**
	 * Performs saturating unsigned subtraction of two V128 vectors across 8 lanes
	 * of 16-bit values.
	 * 
	 * @param b
	 *            The V128 vector to subtract
	 * @return A new V128 with saturated differences of corresponding lanes
	 *         (unsigned)
	 */
	public V128 subSatU16(V128 b) {
		return new V128(subSatU16Half(low, b.low), subSatU16Half(high, b.high));
	}

	private static long subSatU16Half(long a, long b) {
		short lane0 = Util.subSatU16((short)a, (short)b);
		short lane1 = Util.subSatU16((short)(a >>> 16), (short)(b >>> 16));
		short lane2 = Util.subSatU16((short)(a >>> 32), (short)(b >>> 32));
		short lane3 = Util.subSatU16((short)(a >>> 48), (short)(b >>> 48));
		return Short.toUnsignedLong(lane0) | Short.toUnsignedLong(lane1) << 16 | Short.toUnsignedLong(lane2) << 32
			| Short.toUnsignedLong(lane3) << 48;
	}

	/**
	 * Multiplies two V128 vectors across 8 lanes of 16-bit values. Results wrap on
	 * overflow.
	 * 
	 * @param b
	 *            The second V128 vector to multiply with
	 * @return A new V128 with the products of corresponding lanes
	 */
	public V128 mul16(V128 b) {
		return new V128(mul16Half(low, b.low), mul16Half(high, b.high));
	}

	private static long mul16Half(long a, long b) {
		short lane0 = (short)((short)a * (short)b);
		short lane1 = (short)((short)(a >>> 16) * (short)(b >>> 16));
		short lane2 = (short)((short)(a >>> 32) * (short)(b >>> 32));
		short lane3 = (short)((short)(a >>> 48) * (short)(b >>> 48));
		return Short.toUnsignedLong(lane0) | Short.toUnsignedLong(lane1) << 16 | Short.toUnsignedLong(lane2) << 32
			| Short.toUnsignedLong(lane3) << 48;
	}

	/**
	 * Multiplies two V128 vectors across 4 lanes of 32-bit values. Results wrap on
	 * overflow.
	 * 
	 * @param b
	 *            The second V128 vector to multiply with
	 * @return A new V128 with the products of corresponding lanes
	 */
	public V128 mul32(V128 b) {
		return new V128(mul32Half(low, b.low), mul32Half(high, b.high));
	}

	private static long mul32Half(long a, long b) {
		int lane0 = (int)a * (int)b;
		int lane1 = (int)(a >>> 32) * (int)(b >>> 32);
		return Integer.toUnsignedLong(lane0) | Integer.toUnsignedLong(lane1) << 32;
	}

	/**
	 * Multiplies two V128 vectors across 2 lanes of 64-bit values. Results wrap on
	 * overflow.
	 * 
	 * @param b
	 *            The second V128 vector to multiply with
	 * @return A new V128 with the products of corresponding lanes
	 */
	public V128 mul64(V128 b) {
		return new V128(low * b.low, high * b.high);
	}

	/**
	 * Multiplies the lower 8 lanes of two V128 vectors as signed 8-bit values,
	 * producing 16-bit results.
	 * 
	 * @param b
	 *            The second V128 vector to multiply with
	 * @return A new V128 with 8 lanes containing the products of the lower 8 lanes
	 */
	public V128 extmulLowS8(V128 b) {
		return new V128(
			Short.toUnsignedLong((short)((byte)low * (byte)b.low))
				| Short.toUnsignedLong((short)((byte)(low >>> 8) * (byte)(b.low >>> 8))) << 16
				| Short.toUnsignedLong((short)((byte)(low >>> 16) * (byte)(b.low >>> 16))) << 32
				| Short.toUnsignedLong((short)((byte)(low >>> 24) * (byte)(b.low >>> 24))) << 48,
			Short.toUnsignedLong((short)((byte)(low >>> 32) * (byte)(b.low >>> 32)))
				| Short.toUnsignedLong((short)((byte)(low >>> 40) * (byte)(b.low >>> 40))) << 16
				| Short.toUnsignedLong((short)((byte)(low >>> 48) * (byte)(b.low >>> 48))) << 32
				| Short.toUnsignedLong((short)((byte)(low >>> 56) * (byte)(b.low >>> 56))) << 48
		);
	}

	/**
	 * Multiplies the upper 8 lanes of two V128 vectors as signed 8-bit values,
	 * producing 16-bit results.
	 * 
	 * @param b
	 *            The second V128 vector to multiply with
	 * @return A new V128 with 8 lanes containing the products of the upper 8 lanes
	 */
	public V128 extmulHighS8(V128 b) {
		return new V128(
			Short.toUnsignedLong((short)((byte)high * (byte)b.high))
				| Short.toUnsignedLong((short)((byte)(high >>> 8) * (byte)(b.high >>> 8))) << 16
				| Short.toUnsignedLong((short)((byte)(high >>> 16) * (byte)(b.high >>> 16))) << 32
				| Short.toUnsignedLong((short)((byte)(high >>> 24) * (byte)(b.high >>> 24))) << 48,
			Short.toUnsignedLong((short)((byte)(high >>> 32) * (byte)(b.high >>> 32)))
				| Short.toUnsignedLong((short)((byte)(high >>> 40) * (byte)(b.high >>> 40))) << 16
				| Short.toUnsignedLong((short)((byte)(high >>> 48) * (byte)(b.high >>> 48))) << 32
				| Short.toUnsignedLong((short)((byte)(high >>> 56) * (byte)(b.high >>> 56))) << 48
		);
	}

	/**
	 * Multiplies the lower 8 lanes of two V128 vectors as unsigned 8-bit values,
	 * producing 16-bit results.
	 * 
	 * @param b
	 *            The second V128 vector to multiply with
	 * @return A new V128 with 8 lanes containing the products of the lower 8 lanes
	 */
	public V128 extmulLowU8(V128 b) {
		return new V128(
			Short.toUnsignedLong((short)(Byte.toUnsignedInt((byte)low) * Byte.toUnsignedInt((byte)b.low)))
				| Short.toUnsignedLong(
					(short)(Byte.toUnsignedInt((byte)(low >>> 8)) * Byte.toUnsignedInt((byte)(b.low >>> 8)))
				) << 16
				| Short.toUnsignedLong(
					(short)(Byte.toUnsignedInt((byte)(low >>> 16)) * Byte.toUnsignedInt((byte)(b.low >>> 16)))
				) << 32
				| Short.toUnsignedLong(
					(short)(Byte.toUnsignedInt((byte)(low >>> 24)) * Byte.toUnsignedInt((byte)(b.low >>> 24)))
				) << 48,
			Short
				.toUnsignedLong(
					(short)(Byte.toUnsignedInt((byte)(low >>> 32)) * Byte.toUnsignedInt((byte)(b.low >>> 32)))
				)
				| Short.toUnsignedLong(
					(short)(Byte.toUnsignedInt((byte)(low >>> 40)) * Byte.toUnsignedInt((byte)(b.low >>> 40)))
				) << 16
				| Short.toUnsignedLong(
					(short)(Byte.toUnsignedInt((byte)(low >>> 48)) * Byte.toUnsignedInt((byte)(b.low >>> 48)))
				) << 32
				| Short.toUnsignedLong(
					(short)(Byte.toUnsignedInt((byte)(low >>> 56)) * Byte.toUnsignedInt((byte)(b.low >>> 56)))
				) << 48
		);
	}

	/**
	 * Multiplies the upper 8 lanes of two V128 vectors as unsigned 8-bit values,
	 * producing 16-bit results.
	 * 
	 * @param b
	 *            The second V128 vector to multiply with
	 * @return A new V128 with 8 lanes containing the products of the upper 8 lanes
	 */
	public V128 extmulHighU8(V128 b) {
		return new V128(
			Short.toUnsignedLong((short)(Byte.toUnsignedInt((byte)high) * Byte.toUnsignedInt((byte)b.high)))
				| Short.toUnsignedLong(
					(short)(Byte.toUnsignedInt((byte)(high >>> 8)) * Byte.toUnsignedInt((byte)(b.high >>> 8)))
				) << 16
				| Short.toUnsignedLong(
					(short)(Byte.toUnsignedInt((byte)(high >>> 16)) * Byte.toUnsignedInt((byte)(b.high >>> 16)))
				) << 32
				| Short.toUnsignedLong(
					(short)(Byte.toUnsignedInt((byte)(high >>> 24)) * Byte.toUnsignedInt((byte)(b.high >>> 24)))
				) << 48,
			Short
				.toUnsignedLong(
					(short)(Byte.toUnsignedInt((byte)(high >>> 32)) * Byte.toUnsignedInt((byte)(b.high >>> 32)))
				)
				| Short.toUnsignedLong(
					(short)(Byte.toUnsignedInt((byte)(high >>> 40)) * Byte.toUnsignedInt((byte)(b.high >>> 40)))
				) << 16
				| Short.toUnsignedLong(
					(short)(Byte.toUnsignedInt((byte)(high >>> 48)) * Byte.toUnsignedInt((byte)(b.high >>> 48)))
				) << 32
				| Short.toUnsignedLong(
					(short)(Byte.toUnsignedInt((byte)(high >>> 56)) * Byte.toUnsignedInt((byte)(b.high >>> 56)))
				) << 48
		);
	}

	/**
	 * Sums adjacent pairs of signed 8-bit values from a V128 vector, producing
	 * 16-bit results.
	 * 
	 * @return A new V128 with 8 lanes containing the sums of adjacent pairs
	 */
	public V128 extaddPairwiseS8() {
		return new V128(
			Short.toUnsignedLong((short)((byte)low + (byte)high))
				| Short.toUnsignedLong((short)((byte)(low >>> 8) + (byte)(high >>> 8))) << 16
				| Short.toUnsignedLong((short)((byte)(low >>> 16) + (byte)(high >>> 16))) << 32
				| Short.toUnsignedLong((short)((byte)(low >>> 24) + (byte)(high >>> 24))) << 48,
			Short.toUnsignedLong((short)((byte)(low >>> 32) + (byte)(high >>> 32)))
				| Short.toUnsignedLong((short)((byte)(low >>> 40) + (byte)(high >>> 40))) << 16
				| Short.toUnsignedLong((short)((byte)(low >>> 48) + (byte)(high >>> 48))) << 32
				| Short.toUnsignedLong((short)((byte)(low >>> 56) + (byte)(high >>> 56))) << 48
		);
	}

	/**
	 * Sums adjacent pairs of unsigned 8-bit values from a V128 vector, producing
	 * 16-bit results.
	 * 
	 * @return A new V128 with 8 lanes containing the sums of adjacent pairs
	 */
	public V128 extaddPairwiseU8() {
		return new V128(
			Short.toUnsignedLong((short)(Byte.toUnsignedInt((byte)low) + Byte.toUnsignedInt((byte)high)))
				| Short.toUnsignedLong(
					(short)(Byte.toUnsignedInt((byte)(low >>> 8)) + Byte.toUnsignedInt((byte)(high >>> 8)))
				) << 16
				| Short.toUnsignedLong(
					(short)(Byte.toUnsignedInt((byte)(low >>> 16)) + Byte.toUnsignedInt((byte)(high >>> 16)))
				) << 32
				| Short.toUnsignedLong(
					(short)(Byte.toUnsignedInt((byte)(low >>> 24)) + Byte.toUnsignedInt((byte)(high >>> 24)))
				) << 48,
			Short
				.toUnsignedLong(
					(short)(Byte.toUnsignedInt((byte)(low >>> 32)) + Byte.toUnsignedInt((byte)(high >>> 32)))
				)
				| Short.toUnsignedLong(
					(short)(Byte.toUnsignedInt((byte)(low >>> 40)) + Byte.toUnsignedInt((byte)(high >>> 40)))
				) << 16
				| Short.toUnsignedLong(
					(short)(Byte.toUnsignedInt((byte)(low >>> 48)) + Byte.toUnsignedInt((byte)(high >>> 48)))
				) << 32
				| Short.toUnsignedLong(
					(short)(Byte.toUnsignedInt((byte)(low >>> 56)) + Byte.toUnsignedInt((byte)(high >>> 56)))
				) << 48
		);
	}

	/**
	 * Multiplies the lower 4 lanes of two V128 vectors as signed 16-bit values,
	 * producing 32-bit results.
	 * 
	 * @param b
	 *            The second V128 vector to multiply with
	 * @return A new V128 with 4 lanes containing the products of the lower 4 lanes
	 */
	public V128 extmulLowS16(V128 b) {
		return new V128(
			Integer.toUnsignedLong((short)low * (short)b.low)
				| Integer.toUnsignedLong((short)(low >>> 16) * (short)(b.low >>> 16)) << 32,
			Integer.toUnsignedLong((short)(low >>> 32) * (short)(b.low >>> 32))
				| Integer.toUnsignedLong((short)(low >>> 48) * (short)(b.low >>> 48)) << 32
		);
	}

	/**
	 * Multiplies the upper 4 lanes of two V128 vectors as signed 16-bit values,
	 * producing 32-bit results.
	 * 
	 * @param b
	 *            The second V128 vector to multiply with
	 * @return A new V128 with 4 lanes containing the products of the upper 4 lanes
	 */
	public V128 extmulHighS16(V128 b) {
		return new V128(
			Integer.toUnsignedLong((short)high * (short)b.high)
				| Integer.toUnsignedLong((short)(high >>> 16) * (short)(b.high >>> 16)) << 32,
			Integer.toUnsignedLong((short)(high >>> 32) * (short)(b.high >>> 32))
				| Integer.toUnsignedLong((short)(high >>> 48) * (short)(b.high >>> 48)) << 32
		);
	}

	/**
	 * Multiplies the lower 4 lanes of two V128 vectors as unsigned 16-bit values,
	 * producing 32-bit results.
	 * 
	 * @param b
	 *            The second V128 vector to multiply with
	 * @return A new V128 with 4 lanes containing the products of the lower 4 lanes
	 */
	public V128 extmulLowU16(V128 b) {
		return new V128(
			Integer.toUnsignedLong(Short.toUnsignedInt((short)low) * Short.toUnsignedInt((short)b.low))
				| Integer.toUnsignedLong(
					Short.toUnsignedInt((short)(low >>> 16)) * Short.toUnsignedInt((short)(b.low >>> 16))
				) << 32,
			Integer
				.toUnsignedLong(Short.toUnsignedInt((short)(low >>> 32)) * Short.toUnsignedInt((short)(b.low >>> 32)))
				| Integer.toUnsignedLong(
					Short.toUnsignedInt((short)(low >>> 48)) * Short.toUnsignedInt((short)(b.low >>> 48))
				) << 32
		);
	}

	/**
	 * Multiplies the upper 4 lanes of two V128 vectors as unsigned 16-bit values,
	 * producing 32-bit results.
	 * 
	 * @param b
	 *            The second V128 vector to multiply with
	 * @return A new V128 with 4 lanes containing the products of the upper 4 lanes
	 */
	public V128 extmulHighU16(V128 b) {
		return new V128(
			Integer.toUnsignedLong(Short.toUnsignedInt((short)high) * Short.toUnsignedInt((short)b.high))
				| Integer.toUnsignedLong(
					Short.toUnsignedInt((short)(high >>> 16)) * Short.toUnsignedInt((short)(b.high >>> 16))
				) << 32,
			Integer
				.toUnsignedLong(Short.toUnsignedInt((short)(high >>> 32)) * Short.toUnsignedInt((short)(b.high >>> 32)))
				| Integer.toUnsignedLong(
					Short.toUnsignedInt((short)(high >>> 48)) * Short.toUnsignedInt((short)(b.high >>> 48))
				) << 32
		);
	}

	/**
	 * Sums adjacent pairs of signed 16-bit values from a V128 vector, producing
	 * 32-bit results.
	 * 
	 * @return A new V128 with 4 lanes containing the sums of adjacent pairs
	 */
	public V128 extaddPairwiseS16() {
		return new V128(
			Integer.toUnsignedLong((short)low + (short)high)
				| Integer.toUnsignedLong((short)(low >>> 16) + (short)(high >>> 16)) << 32,
			Integer.toUnsignedLong((short)(low >>> 32) + (short)(high >>> 32))
				| Integer.toUnsignedLong((short)(low >>> 48) + (short)(high >>> 48)) << 32
		);
	}

	/**
	 * Sums adjacent pairs of unsigned 16-bit values from a V128 vector, producing
	 * 32-bit results.
	 * 
	 * @return A new V128 with 4 lanes containing the sums of adjacent pairs
	 */
	public V128 extaddPairwiseU16() {
		return new V128(
			Integer.toUnsignedLong(Short.toUnsignedInt((short)low) + Short.toUnsignedInt((short)high))
				| Integer.toUnsignedLong(
					Short.toUnsignedInt((short)(low >>> 16)) + Short.toUnsignedInt((short)(high >>> 16))
				) << 32,
			Integer.toUnsignedLong(Short.toUnsignedInt((short)(low >>> 32)) + Short.toUnsignedInt((short)(high >>> 32)))
				| Integer.toUnsignedLong(
					Short.toUnsignedInt((short)(low >>> 48)) + Short.toUnsignedInt((short)(high >>> 48))
				) << 32
		);
	}

	/**
	 * Multiplies the lower 2 lanes of two V128 vectors as signed 32-bit values,
	 * producing 64-bit results.
	 * 
	 * @param b
	 *            The second V128 vector to multiply with
	 * @return A new V128 with 2 lanes containing the products of the lower 2 lanes
	 */
	public V128 extmulLowS32(V128 b) {
		return new V128(
			(long)((long)(int)low * (long)(int)b.low),
			(long)((long)(int)(low >>> 32) * (long)(int)(b.low >>> 32))
		);
	}

	/**
	 * Multiplies the upper 2 lanes of two V128 vectors as signed 32-bit values,
	 * producing 64-bit results.
	 * 
	 * @param b
	 *            The second V128 vector to multiply with
	 * @return A new V128 with 2 lanes containing the products of the upper 2 lanes
	 */
	public V128 extmulHighS32(V128 b) {
		return new V128(
			(long)((long)(int)high * (long)(int)b.high),
			(long)((long)(int)(high >>> 32) * (long)(int)(b.high >>> 32))
		);
	}

	/**
	 * Multiplies the lower 2 lanes of two V128 vectors as unsigned 32-bit values,
	 * producing 64-bit results.
	 * 
	 * @param b
	 *            The second V128 vector to multiply with
	 * @return A new V128 with 2 lanes containing the products of the lower 2 lanes
	 */
	public V128 extmulLowU32(V128 b) {
		return new V128(
			(long)(Integer.toUnsignedLong((int)low) * Integer.toUnsignedLong((int)b.low)),
			(long)(Integer.toUnsignedLong((int)(low >>> 32)) * Integer.toUnsignedLong((int)(b.low >>> 32)))
		);
	}

	/**
	 * Multiplies the upper 2 lanes of two V128 vectors as unsigned 32-bit values,
	 * producing 64-bit results.
	 * 
	 * @param b
	 *            The second V128 vector to multiply with
	 * @return A new V128 with 2 lanes containing the products of the upper 2 lanes
	 */
	public V128 extmulHighU32(V128 b) {
		return new V128(
			(long)(Integer.toUnsignedLong((int)high) * Integer.toUnsignedLong((int)b.high)),
			(long)(Integer.toUnsignedLong((int)(high >>> 32)) * Integer.toUnsignedLong((int)(b.high >>> 32)))
		);
	}

	/**
	 * Adds two V128 vectors across 4 lanes of 32-bit floats.
	 * 
	 * @param b
	 *            The second V128 vector to add
	 * @return A new V128 with the sums of corresponding lanes
	 */
	public V128 addF32(V128 b) {
		return new V128(addF32Half(low, b.low), addF32Half(high, b.high));
	}

	private static long addF32Half(long a, long b) {
		int lane0 = Float.floatToRawIntBits(Float.sum(Float.intBitsToFloat((int)a), Float.intBitsToFloat((int)b)));
		int lane1 = Float
			.floatToRawIntBits(Float.sum(Float.intBitsToFloat((int)(a >>> 32)), Float.intBitsToFloat((int)(b >>> 32))));
		return Integer.toUnsignedLong(lane0) | Integer.toUnsignedLong(lane1) << 32;
	}

	/**
	 * Subtracts the second V128 vector from the first across 4 lanes of 32-bit
	 * floats.
	 * 
	 * @param b
	 *            The V128 vector to subtract
	 * @return A new V128 with the differences of corresponding lanes
	 */
	public V128 subF32(V128 b) {
		return new V128(subF32Half(low, b.low), subF32Half(high, b.high));
	}

	private static long subF32Half(long a, long b) {
		int lane0 = Float.floatToRawIntBits(Float.intBitsToFloat((int)a) - Float.intBitsToFloat((int)b));
		int lane1 = Float
			.floatToRawIntBits(Float.intBitsToFloat((int)(a >>> 32)) - Float.intBitsToFloat((int)(b >>> 32)));
		return Integer.toUnsignedLong(lane0) | Integer.toUnsignedLong(lane1) << 32;
	}

	/**
	 * Multiplies two V128 vectors across 4 lanes of 32-bit floats.
	 * 
	 * @param b
	 *            The second V128 vector to multiply with
	 * @return A new V128 with the products of corresponding lanes
	 */
	public V128 mulF32(V128 b) {
		return new V128(mulF32Half(low, b.low), mulF32Half(high, b.high));
	}

	private static long mulF32Half(long a, long b) {
		int lane0 = Float.floatToRawIntBits(Float.intBitsToFloat((int)a) * Float.intBitsToFloat((int)b));
		int lane1 = Float
			.floatToRawIntBits(Float.intBitsToFloat((int)(a >>> 32)) * Float.intBitsToFloat((int)(b >>> 32)));
		return Integer.toUnsignedLong(lane0) | Integer.toUnsignedLong(lane1) << 32;
	}

	/**
	 * Divides the first V128 vector by the second across 4 lanes of 32-bit floats.
	 * 
	 * @param b
	 *            The V128 vector to divide by
	 * @return A new V128 with the quotients of corresponding lanes
	 */
	public V128 divF32(V128 b) {
		return new V128(divF32Half(low, b.low), divF32Half(high, b.high));
	}

	private static long divF32Half(long a, long b) {
		int lane0 = Float.floatToRawIntBits(Float.intBitsToFloat((int)a) / Float.intBitsToFloat((int)b));
		int lane1 = Float
			.floatToRawIntBits(Float.intBitsToFloat((int)(a >>> 32)) / Float.intBitsToFloat((int)(b >>> 32)));
		return Integer.toUnsignedLong(lane0) | Integer.toUnsignedLong(lane1) << 32;
	}

	/**
	 * Computes the IEEE 754 minimum of two V128 vectors across 4 lanes of 32-bit
	 * floats. Propagates NaN, prefers -0 over +0.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 with the minimum of corresponding lanes
	 */
	public V128 minF32(V128 b) {
		return new V128(minF32Half(low, b.low), minF32Half(high, b.high));
	}

	private static long minF32Half(long a, long b) {
		int lane0 = Float.floatToRawIntBits(Util.min(Float.intBitsToFloat((int)a), Float.intBitsToFloat((int)b)));
		int lane1 = Float
			.floatToRawIntBits(Util.min(Float.intBitsToFloat((int)(a >>> 32)), Float.intBitsToFloat((int)(b >>> 32))));
		return Integer.toUnsignedLong(lane0) | Integer.toUnsignedLong(lane1) << 32;
	}

	/**
	 * Computes the IEEE 754 maximum of two V128 vectors across 4 lanes of 32-bit
	 * floats. Propagates NaN, prefers -0 over +0.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 with the maximum of corresponding lanes
	 */
	public V128 maxF32(V128 b) {
		return new V128(maxF32Half(low, b.low), maxF32Half(high, b.high));
	}

	private static long maxF32Half(long a, long b) {
		int lane0 = Float.floatToRawIntBits(Util.max(Float.intBitsToFloat((int)a), Float.intBitsToFloat((int)b)));
		int lane1 = Float
			.floatToRawIntBits(Util.max(Float.intBitsToFloat((int)(a >>> 32)), Float.intBitsToFloat((int)(b >>> 32))));
		return Integer.toUnsignedLong(lane0) | Integer.toUnsignedLong(lane1) << 32;
	}

	/**
	 * Computes the pseudo-minimum of two V128 vectors across 4 lanes of 32-bit
	 * floats. Returns the other operand if one is NaN, treats +0 and -0 as equal.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 with the pseudo-minimum of corresponding lanes
	 */
	public V128 pminF32(V128 b) {
		return new V128(pminF32Half(low, b.low), pminF32Half(high, b.high));
	}

	private static long pminF32Half(long a, long b) {
		int lane0 = Float.floatToRawIntBits(
			Float.intBitsToFloat((int)b) < Float.intBitsToFloat((int)a)
				? Float.intBitsToFloat((int)b)
				: Float.intBitsToFloat((int)a)
		);
		int lane1 = Float.floatToRawIntBits(
			Float.intBitsToFloat((int)(b >>> 32)) < Float.intBitsToFloat((int)(a >>> 32))
				? Float.intBitsToFloat((int)(b >>> 32))
				: Float.intBitsToFloat((int)(a >>> 32))
		);
		return Integer.toUnsignedLong(lane0) | Integer.toUnsignedLong(lane1) << 32;
	}

	/**
	 * Computes the pseudo-maximum of two V128 vectors across 4 lanes of 32-bit
	 * floats. Returns the other operand if one is NaN, treats +0 and -0 as equal.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 with the pseudo-maximum of corresponding lanes
	 */
	public V128 pmaxF32(V128 b) {
		return new V128(pmaxF32Half(low, b.low), pmaxF32Half(high, b.high));
	}

	private static long pmaxF32Half(long a, long b) {
		int lane0 = Float.floatToRawIntBits(
			Float.intBitsToFloat((int)a) < Float.intBitsToFloat((int)b)
				? Float.intBitsToFloat((int)b)
				: Float.intBitsToFloat((int)a)
		);
		int lane1 = Float.floatToRawIntBits(
			Float.intBitsToFloat((int)(a >>> 32)) < Float.intBitsToFloat((int)(b >>> 32))
				? Float.intBitsToFloat((int)(b >>> 32))
				: Float.intBitsToFloat((int)(a >>> 32))
		);
		return Integer.toUnsignedLong(lane0) | Integer.toUnsignedLong(lane1) << 32;
	}

	/**
	 * Adds two V128 vectors across 2 lanes of 64-bit floats.
	 * 
	 * @param b
	 *            The second V128 vector to add
	 * @return A new V128 with the sums of corresponding lanes
	 */
	public V128 addF64(V128 b) {
		return new V128(
			Double.doubleToRawLongBits(Double.sum(Double.longBitsToDouble(low), Double.longBitsToDouble(b.low))),
			Double.doubleToRawLongBits(Double.sum(Double.longBitsToDouble(high), Double.longBitsToDouble(b.high)))
		);
	}

	/**
	 * Subtracts the second V128 vector from the first across 2 lanes of 64-bit
	 * floats.
	 * 
	 * @param b
	 *            The V128 vector to subtract
	 * @return A new V128 with the differences of corresponding lanes
	 */
	public V128 subF64(V128 b) {
		return new V128(
			Double.doubleToRawLongBits(Double.longBitsToDouble(low) - Double.longBitsToDouble(b.low)),
			Double.doubleToRawLongBits(Double.longBitsToDouble(high) - Double.longBitsToDouble(b.high))
		);
	}

	/**
	 * Multiplies two V128 vectors across 2 lanes of 64-bit floats.
	 * 
	 * @param b
	 *            The second V128 vector to multiply with
	 * @return A new V128 with the products of corresponding lanes
	 */
	public V128 mulF64(V128 b) {
		return new V128(
			Double.doubleToRawLongBits(Double.longBitsToDouble(low) * Double.longBitsToDouble(b.low)),
			Double.doubleToRawLongBits(Double.longBitsToDouble(high) * Double.longBitsToDouble(b.high))
		);
	}

	/**
	 * Divides the first V128 vector by the second across 2 lanes of 64-bit floats.
	 * 
	 * @param b
	 *            The V128 vector to divide by
	 * @return A new V128 with the quotients of corresponding lanes
	 */
	public V128 divF64(V128 b) {
		return new V128(
			Double.doubleToRawLongBits(Double.longBitsToDouble(low) / Double.longBitsToDouble(b.low)),
			Double.doubleToRawLongBits(Double.longBitsToDouble(high) / Double.longBitsToDouble(b.high))
		);
	}

	/**
	 * Computes the IEEE 754 minimum of two V128 vectors across 2 lanes of 64-bit
	 * floats. Propagates NaN, prefers -0 over +0.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 with the minimum of corresponding lanes
	 */
	public V128 minF64(V128 b) {
		return new V128(
			Double.doubleToRawLongBits(Util.min(Double.longBitsToDouble(low), Double.longBitsToDouble(b.low))),
			Double.doubleToRawLongBits(Util.min(Double.longBitsToDouble(high), Double.longBitsToDouble(b.high)))
		);
	}

	/**
	 * Computes the IEEE 754 maximum of two V128 vectors across 2 lanes of 64-bit
	 * floats. Propagates NaN, prefers -0 over +0.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 with the maximum of corresponding lanes
	 */
	public V128 maxF64(V128 b) {
		return new V128(
			Double.doubleToRawLongBits(Util.max(Double.longBitsToDouble(low), Double.longBitsToDouble(b.low))),
			Double.doubleToRawLongBits(Util.max(Double.longBitsToDouble(high), Double.longBitsToDouble(b.high)))
		);
	}

	/**
	 * Computes the pseudo-minimum of two V128 vectors across 2 lanes of 64-bit
	 * floats. Returns the other operand if one is NaN, treats +0 and -0 as equal.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 with the pseudo-minimum of corresponding lanes
	 */
	public V128 pminF64(V128 b) {
		return new V128(
			Double.doubleToRawLongBits(
				Double.longBitsToDouble(b.low) < Double.longBitsToDouble(low)
					? Double.longBitsToDouble(b.low)
					: Double.longBitsToDouble(low)
			),
			Double.doubleToRawLongBits(
				Double.longBitsToDouble(b.high) < Double.longBitsToDouble(high)
					? Double.longBitsToDouble(b.high)
					: Double.longBitsToDouble(high)
			)
		);
	}

	/**
	 * Computes the pseudo-maximum of two V128 vectors across 2 lanes of 64-bit
	 * floats. Returns the other operand if one is NaN, treats +0 and -0 as equal.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 with the pseudo-maximum of corresponding lanes
	 */
	public V128 pmaxF64(V128 b) {
		return new V128(
			Double.doubleToRawLongBits(
				Double.longBitsToDouble(low) < Double.longBitsToDouble(b.low)
					? Double.longBitsToDouble(b.low)
					: Double.longBitsToDouble(low)
			),
			Double.doubleToRawLongBits(
				Double.longBitsToDouble(high) < Double.longBitsToDouble(b.high)
					? Double.longBitsToDouble(b.high)
					: Double.longBitsToDouble(high)
			)
		);
	}

	/**
	 * Compares two V128 vectors for equality across 4 lanes of 32-bit floats.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 where each lane contains -1 if equal, 0 if not equal
	 */
	public V128 equalsF32(V128 b) {
		return new V128(
			Integer.toUnsignedLong(Float.intBitsToFloat((int)low) == Float.intBitsToFloat((int)b.low) ? -1 : 0)
				| Integer.toUnsignedLong(
					Float.intBitsToFloat((int)(low >>> 32)) == Float.intBitsToFloat((int)(b.low >>> 32)) ? -1 : 0
				) << 32,
			Integer.toUnsignedLong(Float.intBitsToFloat((int)high) == Float.intBitsToFloat((int)b.high) ? -1 : 0)
				| Integer.toUnsignedLong(
					Float.intBitsToFloat((int)(high >>> 32)) == Float.intBitsToFloat((int)(b.high >>> 32)) ? -1 : 0
				) << 32
		);
	}

	/**
	 * Compares two V128 vectors for inequality across 4 lanes of 32-bit floats.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 where each lane contains -1 if not equal, 0 if equal
	 */
	public V128 notEqualsF32(V128 b) {
		return new V128(
			Integer.toUnsignedLong(Float.intBitsToFloat((int)low) != Float.intBitsToFloat((int)b.low) ? -1 : 0)
				| Integer.toUnsignedLong(
					Float.intBitsToFloat((int)(low >>> 32)) != Float.intBitsToFloat((int)(b.low >>> 32)) ? -1 : 0
				) << 32,
			Integer.toUnsignedLong(Float.intBitsToFloat((int)high) != Float.intBitsToFloat((int)b.high) ? -1 : 0)
				| Integer.toUnsignedLong(
					Float.intBitsToFloat((int)(high >>> 32)) != Float.intBitsToFloat((int)(b.high >>> 32)) ? -1 : 0
				) << 32
		);
	}

	/**
	 * Compares two V128 vectors for less-than across 4 lanes of 32-bit floats.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 where each lane contains -1 if less than, 0 otherwise
	 */
	public V128 lessThanF32(V128 b) {
		return new V128(
			Integer.toUnsignedLong(Float.intBitsToFloat((int)low) < Float.intBitsToFloat((int)b.low) ? -1 : 0)
				| Integer.toUnsignedLong(
					Float.intBitsToFloat((int)(low >>> 32)) < Float.intBitsToFloat((int)(b.low >>> 32)) ? -1 : 0
				) << 32,
			Integer.toUnsignedLong(Float.intBitsToFloat((int)high) < Float.intBitsToFloat((int)b.high) ? -1 : 0)
				| Integer.toUnsignedLong(
					Float.intBitsToFloat((int)(high >>> 32)) < Float.intBitsToFloat((int)(b.high >>> 32)) ? -1 : 0
				) << 32
		);
	}

	/**
	 * Compares two V128 vectors for greater-than across 4 lanes of 32-bit floats.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 where each lane contains -1 if greater than, 0 otherwise
	 */
	public V128 greaterThanF32(V128 b) {
		return new V128(
			Integer.toUnsignedLong(Float.intBitsToFloat((int)low) > Float.intBitsToFloat((int)b.low) ? -1 : 0)
				| Integer.toUnsignedLong(
					Float.intBitsToFloat((int)(low >>> 32)) > Float.intBitsToFloat((int)(b.low >>> 32)) ? -1 : 0
				) << 32,
			Integer.toUnsignedLong(Float.intBitsToFloat((int)high) > Float.intBitsToFloat((int)b.high) ? -1 : 0)
				| Integer.toUnsignedLong(
					Float.intBitsToFloat((int)(high >>> 32)) > Float.intBitsToFloat((int)(b.high >>> 32)) ? -1 : 0
				) << 32
		);
	}

	/**
	 * Compares two V128 vectors for less-than-or-equal across 4 lanes of 32-bit
	 * floats.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 where each lane contains -1 if less than or equal, 0
	 *         otherwise
	 */
	public V128 lessThanOrEqualF32(V128 b) {
		return new V128(
			Integer.toUnsignedLong(Float.intBitsToFloat((int)low) <= Float.intBitsToFloat((int)b.low) ? -1 : 0)
				| Integer.toUnsignedLong(
					Float.intBitsToFloat((int)(low >>> 32)) <= Float.intBitsToFloat((int)(b.low >>> 32)) ? -1 : 0
				) << 32,
			Integer.toUnsignedLong(Float.intBitsToFloat((int)high) <= Float.intBitsToFloat((int)b.high) ? -1 : 0)
				| Integer.toUnsignedLong(
					Float.intBitsToFloat((int)(high >>> 32)) <= Float.intBitsToFloat((int)(b.high >>> 32)) ? -1 : 0
				) << 32
		);
	}

	/**
	 * Compares two V128 vectors for greater-than-or-equal across 4 lanes of 32-bit
	 * floats.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 where each lane contains -1 if greater than or equal, 0
	 *         otherwise
	 */
	public V128 greaterThanOrEqualF32(V128 b) {
		return new V128(
			Integer.toUnsignedLong(Float.intBitsToFloat((int)low) >= Float.intBitsToFloat((int)b.low) ? -1 : 0)
				| Integer.toUnsignedLong(
					Float.intBitsToFloat((int)(low >>> 32)) >= Float.intBitsToFloat((int)(b.low >>> 32)) ? -1 : 0
				) << 32,
			Integer.toUnsignedLong(Float.intBitsToFloat((int)high) >= Float.intBitsToFloat((int)b.high) ? -1 : 0)
				| Integer.toUnsignedLong(
					Float.intBitsToFloat((int)(high >>> 32)) >= Float.intBitsToFloat((int)(b.high >>> 32)) ? -1 : 0
				) << 32
		);
	}

	/**
	 * Compares two V128 vectors for equality across 2 lanes of 64-bit floats.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 where each lane contains -1 if equal, 0 if not equal
	 */
	public V128 equalsF64(V128 b) {
		return new V128(
			(long)(Double.longBitsToDouble(low) == Double.longBitsToDouble(b.low) ? -1 : 0),
			(long)(Double.longBitsToDouble(high) == Double.longBitsToDouble(b.high) ? -1 : 0)
		);
	}

	/**
	 * Compares two V128 vectors for inequality across 2 lanes of 64-bit floats.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 where each lane contains -1 if not equal, 0 if equal
	 */
	public V128 notEqualsF64(V128 b) {
		return new V128(
			(long)(Double.longBitsToDouble(low) != Double.longBitsToDouble(b.low) ? -1 : 0),
			(long)(Double.longBitsToDouble(high) != Double.longBitsToDouble(b.high) ? -1 : 0)
		);
	}

	/**
	 * Compares two V128 vectors for less-than across 2 lanes of 64-bit floats.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 where each lane contains -1 if less than, 0 otherwise
	 */
	public V128 lessThanF64(V128 b) {
		return new V128(
			(long)(Double.longBitsToDouble(low) < Double.longBitsToDouble(b.low) ? -1 : 0),
			(long)(Double.longBitsToDouble(high) < Double.longBitsToDouble(b.high) ? -1 : 0)
		);
	}

	/**
	 * Compares two V128 vectors for greater-than across 2 lanes of 64-bit floats.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 where each lane contains -1 if greater than, 0 otherwise
	 */
	public V128 greaterThanF64(V128 b) {
		return new V128(
			(long)(Double.longBitsToDouble(low) > Double.longBitsToDouble(b.low) ? -1 : 0),
			(long)(Double.longBitsToDouble(high) > Double.longBitsToDouble(b.high) ? -1 : 0)
		);
	}

	/**
	 * Compares two V128 vectors for less-than-or-equal across 2 lanes of 64-bit
	 * floats.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 where each lane contains -1 if less than or equal, 0
	 *         otherwise
	 */
	public V128 lessThanOrEqualF64(V128 b) {
		return new V128(
			(long)(Double.longBitsToDouble(low) <= Double.longBitsToDouble(b.low) ? -1 : 0),
			(long)(Double.longBitsToDouble(high) <= Double.longBitsToDouble(b.high) ? -1 : 0)
		);
	}

	/**
	 * Compares two V128 vectors for greater-than-or-equal across 2 lanes of 64-bit
	 * floats.
	 * 
	 * @param b
	 *            The second V128 vector to compare
	 * @return A new V128 where each lane contains -1 if greater than or equal, 0
	 *         otherwise
	 */
	public V128 greaterThanOrEqualF64(V128 b) {
		return new V128(
			(long)(Double.longBitsToDouble(low) >= Double.longBitsToDouble(b.low) ? -1 : 0),
			(long)(Double.longBitsToDouble(high) >= Double.longBitsToDouble(b.high) ? -1 : 0)
		);
	}

	/**
	 * Computes the absolute value of each 32-bit float lane in the V128 vector.
	 * 
	 * @return A new V128 with absolute values in each of the 4 lanes
	 */
	public V128 absF32() {
		return new V128(low & ~0x8000000080000000L, high & ~0x8000000080000000L);
	}

	/**
	 * Negates each 32-bit float lane in the V128 vector.
	 * 
	 * @return A new V128 with negated values in each of the 4 lanes
	 */
	public V128 negF32() {
		return new V128(low ^ 0x8000000080000000L, high ^ 0x8000000080000000L);
	}

	/**
	 * Computes the square root of each 32-bit float lane in the V128 vector.
	 * 
	 * @return A new V128 with square roots in each of the 4 lanes
	 */
	public V128 sqrtF32() {
		return new V128(
			Integer.toUnsignedLong(Float.floatToRawIntBits((float)Math.sqrt(Float.intBitsToFloat((int)low))))
				| ((long)Float.floatToRawIntBits((float)Math.sqrt(Float.intBitsToFloat((int)(low >>> 32)))) << 32),
			Integer.toUnsignedLong(Float.floatToRawIntBits((float)Math.sqrt(Float.intBitsToFloat((int)high))))
				| ((long)Float.floatToRawIntBits((float)Math.sqrt(Float.intBitsToFloat((int)(high >>> 32)))) << 32)
		);
	}

	/**
	 * Rounds each 32-bit float lane in the V128 vector up to the nearest integer.
	 * 
	 * @return A new V128 with ceiling values in each of the 4 lanes
	 */
	public V128 ceilF32() {
		return new V128(
			Integer.toUnsignedLong(Float.floatToRawIntBits(Util.ceilF32(Float.intBitsToFloat((int)low))))
				| ((long)Float.floatToRawIntBits(Util.ceilF32(Float.intBitsToFloat((int)(low >>> 32)))) << 32),
			Integer.toUnsignedLong(Float.floatToRawIntBits(Util.ceilF32(Float.intBitsToFloat((int)high))))
				| ((long)Float.floatToRawIntBits(Util.ceilF32(Float.intBitsToFloat((int)(high >>> 32)))) << 32)
		);
	}

	/**
	 * Rounds each 32-bit float lane in the V128 vector down to the nearest integer.
	 * 
	 * @return A new V128 with floor values in each of the 4 lanes
	 */
	public V128 floorF32() {
		return new V128(
			Integer.toUnsignedLong(Float.floatToRawIntBits(Util.floorF32(Float.intBitsToFloat((int)low))))
				| ((long)Float.floatToRawIntBits(Util.floorF32(Float.intBitsToFloat((int)(low >>> 32)))) << 32),
			Integer.toUnsignedLong(Float.floatToRawIntBits(Util.floorF32(Float.intBitsToFloat((int)high))))
				| ((long)Float.floatToRawIntBits(Util.floorF32(Float.intBitsToFloat((int)(high >>> 32)))) << 32)
		);
	}

	/**
	 * Truncates each 32-bit float lane in the V128 vector toward zero.
	 * 
	 * @return A new V128 with truncated values in each of the 4 lanes
	 */
	public V128 truncF32() {
		return new V128(
			Integer.toUnsignedLong(Float.floatToRawIntBits(Util.truncF32(Float.intBitsToFloat((int)low))))
				| ((long)Float.floatToRawIntBits(Util.truncF32(Float.intBitsToFloat((int)(low >>> 32)))) << 32),
			Integer.toUnsignedLong(Float.floatToRawIntBits(Util.truncF32(Float.intBitsToFloat((int)high))))
				| ((long)Float.floatToRawIntBits(Util.truncF32(Float.intBitsToFloat((int)(high >>> 32)))) << 32)
		);
	}

	/**
	 * Rounds each 32-bit float lane in the V128 vector to the nearest integer, with
	 * halfway cases rounding to even.
	 * 
	 * @return A new V128 with nearest integer values in each of the 4 lanes
	 */
	public V128 nearestF32() {
		return new V128(
			Integer.toUnsignedLong(Float.floatToRawIntBits(Util.nearestF32(Float.intBitsToFloat((int)low))))
				| ((long)Float.floatToRawIntBits(Util.nearestF32(Float.intBitsToFloat((int)(low >>> 32)))) << 32),
			Integer.toUnsignedLong(Float.floatToRawIntBits(Util.nearestF32(Float.intBitsToFloat((int)high))))
				| ((long)Float.floatToRawIntBits(Util.nearestF32(Float.intBitsToFloat((int)(high >>> 32)))) << 32)
		);
	}

	/**
	 * Computes the absolute value of each 64-bit float lane in the V128 vector.
	 * 
	 * @return A new V128 with absolute values in each of the 2 lanes
	 */
	public V128 absF64() {
		return new V128(low & ~Long.MIN_VALUE, high & ~Long.MIN_VALUE);
	}

	/**
	 * Negates each 64-bit float lane in the V128 vector.
	 * 
	 * @return A new V128 with negated values in each of the 2 lanes
	 */
	public V128 negF64() {
		return new V128(low ^ Long.MIN_VALUE, high ^ Long.MIN_VALUE);
	}

	/**
	 * Computes the square root of each 64-bit float lane in the V128 vector.
	 * 
	 * @return A new V128 with square roots in each of the 2 lanes
	 */
	public V128 sqrtF64() {
		return new V128(
			Double.doubleToRawLongBits(Math.sqrt(Double.longBitsToDouble(low))),
			Double.doubleToRawLongBits(Math.sqrt(Double.longBitsToDouble(high)))
		);
	}

	/**
	 * Rounds each 64-bit float lane in the V128 vector up to the nearest integer.
	 * 
	 * @return A new V128 with ceiling values in each of the 2 lanes
	 */
	public V128 ceilF64() {
		return new V128(
			Double.doubleToRawLongBits(Util.ceilF64(Double.longBitsToDouble(low))),
			Double.doubleToRawLongBits(Util.ceilF64(Double.longBitsToDouble(high)))
		);
	}

	/**
	 * Rounds each 64-bit float lane in the V128 vector down to the nearest integer.
	 * 
	 * @return A new V128 with floor values in each of the 2 lanes
	 */
	public V128 floorF64() {
		return new V128(
			Double.doubleToRawLongBits(Util.floorF64(Double.longBitsToDouble(low))),
			Double.doubleToRawLongBits(Util.floorF64(Double.longBitsToDouble(high)))
		);
	}

	/**
	 * Truncates each 64-bit float lane in the V128 vector toward zero.
	 * 
	 * @return A new V128 with truncated values in each of the 2 lanes
	 */
	public V128 truncF64() {
		return new V128(
			Double.doubleToRawLongBits(Util.truncF64(Double.longBitsToDouble(low))),
			Double.doubleToRawLongBits(Util.truncF64(Double.longBitsToDouble(high)))
		);
	}

	/**
	 * Rounds each 64-bit float lane in the V128 vector to the nearest integer, with
	 * halfway cases rounding to even.
	 * 
	 * @return A new V128 with nearest integer values in each of the 2 lanes
	 */
	public V128 nearestF64() {
		return new V128(
			Double.doubleToRawLongBits(Util.nearestF64(Double.longBitsToDouble(low))),
			Double.doubleToRawLongBits(Util.nearestF64(Double.longBitsToDouble(high)))
		);
	}

	/**
	 * Performs a relaxed multiply-add on three V128 vectors across 4 lanes of
	 * 32-bit floats. Computes a * b + c with the product rounded before addition.
	 * 
	 * @param b
	 *            The second V128 vector (multiplicand)
	 * @param c
	 *            The third V128 vector (addend)
	 * @return A new V128 with the multiply-add results of corresponding lanes
	 */
	public V128 relaxedMaddF32(V128 b, V128 c) {
		return new V128(
			Integer
				.toUnsignedLong(
					Float
						.floatToRawIntBits(
							(Float.intBitsToFloat((int)low) * Float.intBitsToFloat((int)b.low))
								+ Float.intBitsToFloat((int)c.low)
						)
				)
				| ((long)Float.floatToRawIntBits(
					(Float.intBitsToFloat((int)(low >>> 32)) * Float.intBitsToFloat((int)(b.low >>> 32)))
						+ Float.intBitsToFloat((int)(c.low >>> 32))
				) << 32),
			Integer
				.toUnsignedLong(
					Float
						.floatToRawIntBits(
							(Float.intBitsToFloat((int)high) * Float.intBitsToFloat((int)b.high))
								+ Float.intBitsToFloat((int)c.high)
						)
				)
				| ((long)Float.floatToRawIntBits(
					(Float.intBitsToFloat((int)(high >>> 32)) * Float.intBitsToFloat((int)(b.high >>> 32)))
						+ Float.intBitsToFloat((int)(c.high >>> 32))
				) << 32)
		);
	}

	/**
	 * Performs a relaxed negate-multiply-add on three V128 vectors across 4 lanes
	 * of 32-bit floats. Computes -(a * b) + c with the product rounded before
	 * addition.
	 * 
	 * @param b
	 *            The second V128 vector (multiplicand)
	 * @param c
	 *            The third V128 vector (addend)
	 * @return A new V128 with the negate-multiply-add results of corresponding
	 *         lanes
	 */
	public V128 relaxedNmaddF32(V128 b, V128 c) {
		return new V128(
			Integer
				.toUnsignedLong(
					Float
						.floatToRawIntBits(
							-(Float.intBitsToFloat((int)low) * Float.intBitsToFloat((int)b.low))
								+ Float.intBitsToFloat((int)c.low)
						)
				)
				| ((long)Float.floatToRawIntBits(
					-(Float.intBitsToFloat((int)(low >>> 32)) * Float.intBitsToFloat((int)(b.low >>> 32)))
						+ Float.intBitsToFloat((int)(c.low >>> 32))
				) << 32),
			Integer
				.toUnsignedLong(
					Float
						.floatToRawIntBits(
							-(Float.intBitsToFloat((int)high) * Float.intBitsToFloat((int)b.high))
								+ Float.intBitsToFloat((int)c.high)
						)
				)
				| ((long)Float.floatToRawIntBits(
					-(Float.intBitsToFloat((int)(high >>> 32)) * Float.intBitsToFloat((int)(b.high >>> 32)))
						+ Float.intBitsToFloat((int)(c.high >>> 32))
				) << 32)
		);
	}

	/**
	 * Performs a relaxed multiply-add on three V128 vectors across 2 lanes of
	 * 64-bit floats. Computes a * b + c with the product rounded before addition.
	 * 
	 * @param b
	 *            The second V128 vector (multiplicand)
	 * @param c
	 *            The third V128 vector (addend)
	 * @return A new V128 with the multiply-add results of corresponding lanes
	 */
	public V128 relaxedMaddF64(V128 b, V128 c) {
		return new V128(
			Double.doubleToRawLongBits(
				(Double.longBitsToDouble(low) * Double.longBitsToDouble(b.low)) + Double.longBitsToDouble(c.low)
			),
			Double.doubleToRawLongBits(
				(Double.longBitsToDouble(high) * Double.longBitsToDouble(b.high)) + Double.longBitsToDouble(c.high)
			)
		);
	}

	/**
	 * Performs a relaxed negate-multiply-add on three V128 vectors across 2 lanes
	 * of 64-bit floats. Computes -(a * b) + c with the product rounded before
	 * addition.
	 * 
	 * @param b
	 *            The second V128 vector (multiplicand)
	 * @param c
	 *            The third V128 vector (addend)
	 * @return A new V128 with the negate-multiply-add results of corresponding
	 *         lanes
	 */
	public V128 relaxedNmaddF64(V128 b, V128 c) {
		return new V128(
			Double.doubleToRawLongBits(
				-(Double.longBitsToDouble(low) * Double.longBitsToDouble(b.low)) + Double.longBitsToDouble(c.low)
			),
			Double.doubleToRawLongBits(
				-(Double.longBitsToDouble(high) * Double.longBitsToDouble(b.high)) + Double.longBitsToDouble(c.high)
			)
		);
	}

	/**
	 * Performs a bitwise AND operation on two V128 vectors across 16 lanes of 8-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to AND with
	 * @return A new V128 with the bitwise AND of corresponding lanes
	 */
	public V128 and(V128 b) {
		return new V128(low & b.low, high & b.high);
	}

	/**
	 * Performs a bitwise ANDNOT operation on two V128 vectors across 16 lanes of
	 * 8-bit values. Computes a &amp; ~b for each lane.
	 * 
	 * @param b
	 *            The second V128 vector (negated before AND)
	 * @return A new V128 with the bitwise ANDNOT of corresponding lanes
	 */
	public V128 andNot(V128 b) {
		return new V128(low & ~b.low, high & ~b.high);
	}

	/**
	 * Performs a bitwise OR operation on two V128 vectors across 16 lanes of 8-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to OR with
	 * @return A new V128 with the bitwise OR of corresponding lanes
	 */
	public V128 or(V128 b) {
		return new V128(low | b.low, high | b.high);
	}

	/**
	 * Performs a bitwise XOR operation on two V128 vectors across 16 lanes of 8-bit
	 * values.
	 * 
	 * @param b
	 *            The second V128 vector to XOR with
	 * @return A new V128 with the bitwise XOR of corresponding lanes
	 */
	public V128 xor(V128 b) {
		return new V128(low ^ b.low, high ^ b.high);
	}

	/**
	 * Performs a bitwise NOT operation on a vector.
	 * 
	 * @return The result.
	 */
	public V128 bitNot() {
		return new V128(~low, ~high);
	}

	/**
	 * Performs a bitselect operation on three V128 vectors across 16 lanes of 8-bit
	 * values. For each bit, selects from this if the corresponding bit in c is 1,
	 * from b if 0.
	 * 
	 * @param b
	 *            The second V128 vector (selected when c bit is 0)
	 * @param c
	 *            The third V128 vector (control mask)
	 * @return A new V128 with bits selected from a or b based on c
	 */
	public V128 bitselect(V128 b, V128 c) {
		return new V128((low & c.low) | (b.low & ~c.low), (high & c.high) | (b.high & ~c.high));
	}

	@Override
	public String toString() {
		var sb = new StringBuilder();
		sb.append("V128(");
		for(int i = 0; i < 16; ++i) {
			sb.append(String.format("%02x", (byte)((i < 8 ? low : high) >>> ((i & 7) * 8))));
			if(i < 15) {
				sb.append(", ");
			}
		}
		sb.append(")");
		return sb.toString();
	}
}
