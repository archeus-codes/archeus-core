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
import java.util.regex.Matcher;

/**
 * Decodes codes as {@link ArdsParser} does, plus the types the Nitrohax cheat engine adds (github.com/chishm/nitrohax):
 * {@code C1}, {@code C2}, {@code C4}, {@code C5}, {@code C6}, {@code CF} and {@code D400000x}. A standard Action
 * Replay uses none of them: its only {@code C} type is {@code C0}, and its {@code D4} is always {@code D4000000}, the
 * same as {@link DataOperator#ADD}. So reading them can't change what a standard code means.
 */
public final class NitrohaxParser extends ArdsParser {

	@Override
	protected DecodedLine decodeAt(List<String> codeLines, int index) {
		String line = codeLines.get(index).trim();
		Matcher matcher = LINE_PATTERN.matcher(line);

		if (matcher.matches()) {
			long word1 = Long.parseLong(matcher.group(1), 16);
			long word2 = Long.parseLong(matcher.group(2), 16);
			int type = (int) (word1 >>> 28);
			long address = word1 & ADDRESS_MASK;

			if (type == 0xC) {
				int subOpcode = (int) (address >>> 24);

				if (subOpcode == 0x1) {
					return decodeCallFunction(codeLines, index, address, word2);
				}

				if (subOpcode == 0x2) {
					return decodeRunCode(codeLines, index, address, word2);
				}
			}
		}

		return super.decodeAt(codeLines, index);
	}

	@Override
	protected ArdsInstruction decode(int type, String line, long address, long word2) {
		if (type == 0xC) {
			int subOpcode = (int) (address >>> 24);

			return switch (subOpcode) {
				case 0x4 -> new ArdsInstruction.ScratchSpace(word2);
				case 0x5 -> decodeCounter(word2);
				case 0x6 -> new ArdsInstruction.StoreOffsetToAddress(word2);
				case 0xF -> decodeCfFamily(line, address, word2);
				default -> super.decode(type, line, address, word2);
			};
		}

		if (type == 0xD && (address >>> 24) == 0x4) {
			return decodeDataOperation(line, address, word2);
		}

		return super.decode(type, line, address, word2);
	}

	private DecodedLine decodeCallFunction(List<String> codeLines, int index, long address, long word2) {
		int argCount = (int) (address & 0xF);
		List<String> argumentLines = readAheadLines(codeLines, index, (argCount + 1) / 2);
		return new DecodedLine(new ArdsInstruction.CallFunction(argCount, word2, argumentLines), argumentLines.size() + 1);
	}

	private DecodedLine decodeRunCode(List<String> codeLines, int index, long address, long word2) {
		int mode = (int) (address & 0xF);
		List<String> dataLines = readAheadLines(codeLines, index, (int) ((word2 + 7) / 8));
		return new DecodedLine(new ArdsInstruction.RunCode(mode, word2, dataLines), dataLines.size() + 1);
	}

	private ArdsInstruction decodeCounter(long word2) {
		int compareValue = (int) ((word2 >>> 16) & 0xFFFFL);
		int mask = (int) (word2 & 0xFFFFL);
		return new ArdsInstruction.Counter(compareValue, mask);
	}

	private ArdsInstruction decodeCfFamily(String line, long address, long word2) {
		int subSubOpcode = (int) (address & 0xF);

		return switch (subSubOpcode) {
			case 0x0 -> new ArdsInstruction.CodeListEnd();
			case 0x1 -> new ArdsInstruction.RelocateEngine(word2);
			case 0x2 -> new ArdsInstruction.OverrideHookAddress(word2);
			default -> new ArdsInstruction.Unknown(line, "unrecognized Nitrohax CF sub-opcode \"C%07X\"".formatted(address));
		};
	}

	private ArdsInstruction decodeDataOperation(String line, long address, long word2) {
		int operationSelector = (int) (address & 0xF);

		return DataOperator.fromSelector(operationSelector)
				.<ArdsInstruction>map(operator -> new ArdsInstruction.DataOperation(operator, word2))
				.orElseGet(() -> new ArdsInstruction.Unknown(line, "unrecognized Nitrohax D4 data operation selector \"%X\"".formatted(operationSelector)));
	}
}
