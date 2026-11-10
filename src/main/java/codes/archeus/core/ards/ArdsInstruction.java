/*
 * Copyright 2021-2026 Deiondre Andrews (Demonic722)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package codes.archeus.core.ards;

import java.util.List;

/**
 * One decoded Action Replay DS instruction. Each line decodes to one, except {@link PatchCode}, {@link CallFunction}
 * and {@link RunCode}, which also take the lines that follow.
 *
 * <p>Only {@link NitrohaxParser} decodes the types marked Nitrohax (github.com/chishm/nitrohax). {@link ArdsParser}
 * reads them as {@link Unknown}.
 */
public sealed interface ArdsInstruction {

	/**
	 * Type {@code 0}/{@code 1}/{@code 2}: writes a constant {@code width}-bit value to {@code [address + offset]}.
	 */
	record ConstantWrite(int width, long address, long value) implements ArdsInstruction {
	}

	/**
	 * Type {@code 3}-{@code 6}: 32-bit conditional comparing the word at {@code [address + offset]} with
	 * {@code value}.
	 */
	record Conditional32(ConditionOperator operator, long address, long value) implements ArdsInstruction {
	}

	/**
	 * Type {@code 7}-{@code A}: 16-bit conditional comparing {@code compareValue} with the halfword at
	 * {@code [address + offset]} masked by {@code ~mask}. {@code mask} is the code's {@code ZZZZ} field as written.
	 */
	record Conditional16Masked(ConditionOperator operator, long address, int mask, int compareValue) implements ArdsInstruction {
	}

	/**
	 * Type {@code B}: loads the offset register from {@code [address + offset]}.
	 */
	record LoadOffset(long address) implements ArdsInstruction {
	}

	/**
	 * Type {@code C0}: runs the block up to its {@code D1} or {@code D2} {@code count + 1} times, so
	 * {@code C0000000 00000009} runs it 10 times. Repeats don't nest.
	 */
	record Repeat(long count) implements ArdsInstruction {
	}

	/**
	 * Type {@code D0}: ends the most recently opened conditional block.
	 */
	record EndIf() implements ArdsInstruction {
	}

	/**
	 * Type {@code D1}: ends the repeat block and any conditionals still open inside it.
	 */
	record EndRepeat() implements ArdsInstruction {
	}

	/**
	 * Type {@code D2}: ends every open block and resets the offset and stored registers to zero.
	 */
	record EndCode() implements ArdsInstruction {
	}

	/**
	 * Type {@code D3}: sets the offset register to {@code value}.
	 */
	record SetOffset(long value) implements ArdsInstruction {
	}

	/**
	 * Type {@code D4}: adds {@code value} to the stored register.
	 */
	record AddToStored(long value) implements ArdsInstruction {
	}

	/**
	 * Type {@code D5}: sets the stored register to {@code value}.
	 */
	record SetStored(long value) implements ArdsInstruction {
	}

	/**
	 * Type {@code D6}-{@code D8}: writes the stored register's low {@code width} bits to {@code [address + offset]},
	 * then adds {@code width / 8} to the offset.
	 */
	record StoreAndIncrement(int width, long address) implements ArdsInstruction {
	}

	/**
	 * Type {@code D9}-{@code DB}: loads the stored register with the {@code width}-bit value at
	 * {@code [address + offset]}.
	 */
	record LoadStored(int width, long address) implements ArdsInstruction {
	}

	/**
	 * Type {@code DC}: adds {@code value} to the offset register.
	 */
	record AddToOffset(long value) implements ArdsInstruction {
	}

	/**
	 * Type {@code E}: copies {@code byteCount} bytes from the lines that follow to {@code [address + offset]}.
	 */
	record PatchCode(long address, long byteCount, List<String> dataLines) implements ArdsInstruction {
	}

	/**
	 * Type {@code F}: copies {@code byteCount} bytes from {@code [offset]} to {@code address}, without the offset.
	 */
	record MemoryCopy(long address, long byteCount) implements ArdsInstruction {
	}

	/**
	 * Nitrohax type {@code C1}: calls the function at {@code functionAddress} with 0 to 4 arguments, two a line.
	 */
	record CallFunction(int argCount, long functionAddress, List<String> argumentLines) implements ArdsInstruction {
	}

	/**
	 * Nitrohax type {@code C2}: runs {@code byteLength} bytes of ARM ({@code mode} 0) or THUMB (1) code from the
	 * lines that follow.
	 */
	record RunCode(int mode, long byteLength, List<String> codeLines) implements ArdsInstruction {
	}

	/**
	 * Nitrohax type {@code C4}: points the offset register at this instruction, so {@code [offset + 4]} is
	 * {@code value}'s own word.
	 */
	record ScratchSpace(long value) implements ArdsInstruction {
	}

	/**
	 * Nitrohax type {@code C5}: adds 1 to a counter each run and is true when {@code (counter & mask) == compareValue}.
	 */
	record Counter(int compareValue, int mask) implements ArdsInstruction {
	}

	/**
	 * Nitrohax type {@code C6}: stores the offset register to {@code address}, without the offset.
	 */
	record StoreOffsetToAddress(long address) implements ArdsInstruction {
	}

	/**
	 * Nitrohax type {@code CF000001}: relocates the cheat engine itself to {@code address}.
	 */
	record RelocateEngine(long address) implements ArdsInstruction {
	}

	/**
	 * Nitrohax type {@code CF000002}: replaces the ARM7 VBlank hook with the function pointer at {@code address}.
	 */
	record OverrideHookAddress(long address) implements ArdsInstruction {
	}

	/**
	 * Nitrohax type {@code CF000000}: ends the code list. Nitrohax adds it; codes don't normally hold it.
	 */
	record CodeListEnd() implements ArdsInstruction {
	}

	/**
	 * Nitrohax type {@code D400000x}: applies {@code operator} and {@code value} to the stored register. {@code x} 0 is
	 * the standard {@link AddToStored}.
	 */
	record DataOperation(DataOperator operator, long value) implements ArdsInstruction {
	}

	/**
	 * A line that didn't decode: malformed hex, or a type it doesn't know.
	 */
	record Unknown(String rawLine, String reason) implements ArdsInstruction {
	}
}
