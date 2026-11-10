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

/**
 * Decodes a code with {@link ArdsParser}, then checks its structure and the address each instruction touches, into
 * an {@link ArdsCodeAnalysis}. It reads standard Action Replay DS types only; {@link NitrohaxAnalyzer} adds Nitrohax's.
 */
public class ArdsAnalyzer {
	/** More bytes than any real patch or routine declares, so a misdecode. */
	protected static final long MAX_PLAUSIBLE_BYTE_COUNT = 65536;

	protected final ArdsParser parser;

	public ArdsAnalyzer() {
		this(new ArdsParser());
	}

	protected ArdsAnalyzer(ArdsParser parser) {
		this.parser = parser;
	}

	/**
	 * What {@link #processInstruction} tracks across one code's instructions.
	 */
	protected static final class WalkState {
		final List<String> structuralIssues = new ArrayList<>();
		final List<AddressClassification> addressClassifications = new ArrayList<>();
		int openConditionals = 0;
		int openConditionalsAtRepeatStart = 0;
		boolean repeatOpen = false;
		boolean offsetActive = false;
	}

	/**
	 * Analyzes a code's lines, each {@code XXXXXXXX YYYYYYYY}. It doesn't throw: a bad line is a structural issue.
	 */
	public ArdsCodeAnalysis analyze(List<String> codeLines) {
		List<ArdsInstruction> instructions = parser.parse(codeLines);
		var state = new WalkState();
		int rawLineCursor = 0;

		for (int i = 0; i < instructions.size(); ++i) {
			rawLineCursor += processInstruction(instructions.get(i), i, rawLineCursor, codeLines, state);
		}

		if (state.openConditionals > 0) {
			state.structuralIssues.add("code ends with %d unclosed conditional(s): missing a final D0 or D2, execution status will read false on real hardware".formatted(state.openConditionals));
		}

		if (state.repeatOpen) {
			state.structuralIssues.add("code ends with an unclosed repeat block: missing a final D1 or D2, execution status will read false on real hardware");
		}

		return new ArdsCodeAnalysis(instructions, state.structuralIssues, state.addressClassifications);
	}

	/**
	 * Checks one instruction and updates {@code state}, returning the number of lines it takes. A subclass with more
	 * types overrides this.
	 */
	protected int processInstruction(ArdsInstruction instruction, int index, int rawLineCursor, List<String> codeLines, WalkState state) {
		int consumedRawLines = 1;

		switch (instruction) {
			case ArdsInstruction.ConstantWrite w -> {
				state.addressClassifications.add(classification(index, rawLineCursor, instruction, w.address(), state.offsetActive));
				checkAlignment(state, rawLineCursor, "%d-bit write".formatted(w.width()), w.address(), w.width() / 8);
			}
			case ArdsInstruction.Conditional32 c -> {
				state.addressClassifications.add(classification(index, rawLineCursor, instruction, c.address(), state.offsetActive));
				checkAlignment(state, rawLineCursor, "32-bit compare", c.address(), 4);
				++state.openConditionals;
			}
			case ArdsInstruction.Conditional16Masked c -> {
				state.addressClassifications.add(classification(index, rawLineCursor, instruction, c.address(), state.offsetActive));
				checkAlignment(state, rawLineCursor, "16-bit compare", c.address(), 2);
				++state.openConditionals;
			}
			case ArdsInstruction.LoadOffset lo -> {
				state.addressClassifications.add(classification(index, rawLineCursor, instruction, lo.address(), state.offsetActive));
				state.offsetActive = true;
			}
			case ArdsInstruction.Repeat _ -> {
				if (state.repeatOpen) {
					state.structuralIssues.add("line %d: repeat (C0) opened while another repeat block is already open: the first was never properly closed".formatted(rawLineCursor + 1));
				} else {
					state.repeatOpen = true;
					state.openConditionalsAtRepeatStart = state.openConditionals;
				}
			}
			case ArdsInstruction.EndIf _ -> {
				if (state.openConditionals > 0) {
					--state.openConditionals;
				}
			}
			case ArdsInstruction.EndRepeat _ -> {
				if (state.repeatOpen) {
					state.repeatOpen = false;
					state.openConditionals = state.openConditionalsAtRepeatStart;
				}
			}
			case ArdsInstruction.EndCode _ -> {
				state.repeatOpen = false;
				state.openConditionals = 0;
				state.offsetActive = false;
			}
			case ArdsInstruction.SetOffset _, ArdsInstruction.AddToOffset _ -> state.offsetActive = true;
			case ArdsInstruction.StoreAndIncrement s -> state.addressClassifications.add(classification(index, rawLineCursor, instruction, s.address(), state.offsetActive));
			case ArdsInstruction.LoadStored l -> state.addressClassifications.add(classification(index, rawLineCursor, instruction, l.address(), state.offsetActive));
			case ArdsInstruction.PatchCode p -> {
				consumedRawLines += p.dataLines().size();

				if (p.address() != 0) {
					state.addressClassifications.add(classification(index, rawLineCursor, instruction, p.address(), state.offsetActive));
				}

				checkDeclaredByteCount(state, rawLineCursor, "patch code (E)", p.byteCount(), p.dataLines().size());

				int nextRawLineIndex = rawLineCursor + consumedRawLines;

				if (!p.dataLines().isEmpty() && nextRawLineIndex < codeLines.size()) {
					String nextLine = codeLines.get(nextRawLineIndex).trim();
					String lastDataLine = p.dataLines().getLast().trim();

					if (nextLine.equals(lastDataLine)) {
						state.structuralIssues.add("line %d: duplicates its last data line: likely an erroneous extra copy from patch code (E) at line %d".formatted(nextRawLineIndex + 1, rawLineCursor + 1));
					}
				}
			}
			case ArdsInstruction.MemoryCopy m -> state.addressClassifications.add(classification(index, rawLineCursor, instruction, m.address(), false));
			case ArdsInstruction.Unknown u -> state.structuralIssues.add("line %d: %s".formatted(rawLineCursor + 1, u.reason()));
			default -> {
				// No address to check, and Nitrohax types land here too
			}
		}

		return consumedRawLines;
	}

	/**
	 * Flags a byte count over {@link #MAX_PLAUSIBLE_BYTE_COUNT}, or one needing more lines than
	 * {@code availableLines}.
	 */
	protected void checkDeclaredByteCount(WalkState state, int rawLineCursor, String label, long declaredByteCount, int availableLines) {
		if (declaredByteCount > MAX_PLAUSIBLE_BYTE_COUNT) {
			state.structuralIssues.add("line %d: %s declares an implausible %d byte(s)".formatted(rawLineCursor + 1, label, declaredByteCount));
			return;
		}

		long declaredLines = (declaredByteCount + 7) / 8;

		if (declaredLines > availableLines) {
			state.structuralIssues.add("line %d: %s declares %d byte(s) (%d line(s)) but only %d remain".formatted(rawLineCursor + 1, label, declaredByteCount, declaredLines, availableLines));
		}
	}

	/**
	 * Flags an access whose address isn't a multiple of its size: the DS doesn't access the address as
	 * written. Skipped while an offset is active, since the real address is only known at run time.
	 */
	protected void checkAlignment(WalkState state, int rawLineCursor, String label, long address, int byteSize) {
		if (!state.offsetActive && address % byteSize != 0) {
			state.structuralIssues.add("line %d: %s to %08X isn't %d-byte aligned".formatted(rawLineCursor + 1, label, address, byteSize));
		}
	}

	protected AddressClassification classification(int index, int rawLine, ArdsInstruction instruction, long address, boolean offsetActive) {
		ArdsAddressClassifier.Region region = ArdsAddressClassifier.classify(address);
		return new AddressClassification(index, rawLine, instruction, address, region, offsetActive);
	}
}
