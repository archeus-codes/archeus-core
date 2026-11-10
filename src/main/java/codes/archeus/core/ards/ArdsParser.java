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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decodes a code's lines into {@link ArdsInstruction}s, one a line, except that {@link ArdsInstruction.PatchCode}
 * also takes the lines its byte count needs. A line it can't decode becomes {@link ArdsInstruction.Unknown}.
 * {@link ArdsAnalyzer} checks the structure, such as unclosed blocks.
 *
 * <p>It reads standard Action Replay DS types only; {@link NitrohaxParser} adds Nitrohax's.
 */
public class ArdsParser {
	static final Pattern LINE_PATTERN = Pattern.compile("^([0-9A-Fa-f]{8})\\s([0-9A-Fa-f]{8})$");

	/** A word without its type, the top 4 bits. */
	static final long ADDRESS_MASK = 0x0FFF_FFFFL;

	/**
	 * A decoded instruction and the number of lines it took.
	 */
	protected record DecodedLine(ArdsInstruction instruction, int linesConsumed) {
	}

	/**
	 * Decodes a code's lines, each {@code XXXXXXXX YYYYYYYY}. It doesn't throw: a bad line is
	 * {@link ArdsInstruction.Unknown}.
	 */
	public final List<ArdsInstruction> parse(List<String> codeLines) {
		List<ArdsInstruction> instructions = new ArrayList<>();
		int i = 0;

		while (i < codeLines.size()) {
			DecodedLine decoded = decodeAt(codeLines, i);
			instructions.add(decoded.instruction());
			i += decoded.linesConsumed();
		}

		return instructions;
	}

	/**
	 * Decodes the instruction at {@code index} and any lines it takes after it. A subclass with more types that take
	 * several lines overrides this.
	 */
	protected DecodedLine decodeAt(List<String> codeLines, int index) {
		String line = codeLines.get(index).trim();
		Matcher matcher = LINE_PATTERN.matcher(line);

		if (!matcher.matches()) {
			return new DecodedLine(new ArdsInstruction.Unknown(line, "not a valid \"XXXXXXXX YYYYYYYY\" hex pair"), 1);
		}

		long word1 = Long.parseLong(matcher.group(1), 16);
		long word2 = Long.parseLong(matcher.group(2), 16);
		int type = (int) (word1 >>> 28);
		long address = word1 & ADDRESS_MASK;

		if (type == 0xE) {
			List<String> dataLines = readAheadLines(codeLines, index, (int) ((word2 + 7) / 8));
			return new DecodedLine(new ArdsInstruction.PatchCode(address, word2, dataLines), dataLines.size() + 1);
		}

		return new DecodedLine(decode(type, line, address, word2), 1);
	}

	/**
	 * Up to {@code lineCount} lines after {@code index}, fewer if the code ends first.
	 */
	protected static List<String> readAheadLines(List<String> codeLines, int index, int lineCount) {
		List<String> lines = new ArrayList<>();

		for (int j = 0; j < lineCount && index + 1 + j < codeLines.size(); ++j) {
			lines.add(codeLines.get(index + 1 + j));
		}

		return lines;
	}

	/**
	 * Decodes an instruction that takes one line. A subclass with more such types overrides this.
	 */
	protected ArdsInstruction decode(int type, String line, long address, long word2) {
		return switch (type) {
			case 0x0 -> new ArdsInstruction.ConstantWrite(32, address, word2);
			case 0x1 -> new ArdsInstruction.ConstantWrite(16, address, word2 & 0xFFFFL);
			case 0x2 -> new ArdsInstruction.ConstantWrite(8, address, word2 & 0xFFL);
			case 0x3 -> new ArdsInstruction.Conditional32(ConditionOperator.LESS_THAN, address, word2);
			case 0x4 -> new ArdsInstruction.Conditional32(ConditionOperator.GREATER_THAN, address, word2);
			case 0x5 -> new ArdsInstruction.Conditional32(ConditionOperator.EQUAL, address, word2);
			case 0x6 -> new ArdsInstruction.Conditional32(ConditionOperator.NOT_EQUAL, address, word2);
			case 0x7 -> maskedConditional(ConditionOperator.LESS_THAN, address, word2);
			case 0x8 -> maskedConditional(ConditionOperator.GREATER_THAN, address, word2);
			case 0x9 -> maskedConditional(ConditionOperator.EQUAL, address, word2);
			case 0xA -> maskedConditional(ConditionOperator.NOT_EQUAL, address, word2);
			case 0xB -> new ArdsInstruction.LoadOffset(address);
			case 0xC -> decodeRepeat(line, address, word2);
			case 0xD -> decodeDFamily(line, address, word2);
			case 0xF -> new ArdsInstruction.MemoryCopy(address, word2);
			default -> new ArdsInstruction.Unknown(line, "unreachable: type 0xE is intercepted by decodeAt() before decode() is called, and every other nibble value is covered above");
		};
	}

	private ArdsInstruction maskedConditional(ConditionOperator operator, long address, long word2) {
		int mask = (int) ((word2 >>> 16) & 0xFFFFL);
		int compareValue = (int) (word2 & 0xFFFFL);
		return new ArdsInstruction.Conditional16Masked(operator, address, mask, compareValue);
	}

	private ArdsInstruction decodeRepeat(String line, long address, long word2) {
		if (address != 0) {
			return new ArdsInstruction.Unknown(line, "type C sub-opcode \"%07X\" isn't the documented \"0000000\" (repeat)".formatted(address));
		}

		return new ArdsInstruction.Repeat(word2);
	}

	private ArdsInstruction decodeDFamily(String line, long address, long word2) {
		int subOpcode = (int) (address >>> 24);

		return switch (subOpcode) {
			case 0x0 -> new ArdsInstruction.EndIf();
			case 0x1 -> new ArdsInstruction.EndRepeat();
			case 0x2 -> new ArdsInstruction.EndCode();
			case 0x3 -> new ArdsInstruction.SetOffset(word2);
			case 0x4 -> new ArdsInstruction.AddToStored(word2);
			case 0x5 -> new ArdsInstruction.SetStored(word2);
			case 0x6 -> new ArdsInstruction.StoreAndIncrement(32, word2);
			case 0x7 -> new ArdsInstruction.StoreAndIncrement(16, word2);
			case 0x8 -> new ArdsInstruction.StoreAndIncrement(8, word2);
			case 0x9 -> new ArdsInstruction.LoadStored(32, word2);
			case 0xA -> new ArdsInstruction.LoadStored(16, word2);
			case 0xB -> new ArdsInstruction.LoadStored(8, word2);
			case 0xC -> new ArdsInstruction.AddToOffset(word2);
			default -> new ArdsInstruction.Unknown(line, "unrecognized D-family sub-opcode \"D%X\"".formatted(subOpcode));
		};
	}
}
