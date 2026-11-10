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
 * Analyzes codes as {@link ArdsAnalyzer} does, plus the Nitrohax types {@link NitrohaxParser} decodes.
 *
 * <p>{@code RunCode} has no address, so only its byte count is checked, as with {@code PatchCode}.
 * {@code StoreOffsetToAddress} ignores the offset, as {@code MemoryCopy} does. {@code ScratchSpace}'s value isn't an
 * address, so it isn't checked. The other Nitrohax types aren't checked, since there are no real codes using them to
 * check against.
 */
public final class NitrohaxAnalyzer extends ArdsAnalyzer {

	public NitrohaxAnalyzer() {
		super(new NitrohaxParser());
	}

	@Override
	protected int processInstruction(ArdsInstruction instruction, int index, int rawLineCursor, List<String> codeLines, WalkState state) {
		return switch (instruction) {
			case ArdsInstruction.CallFunction c -> {
				state.addressClassifications.add(classification(index, rawLineCursor, instruction, c.functionAddress(), state.offsetActive));
				yield 1 + c.argumentLines().size();
			}
			case ArdsInstruction.RunCode r -> {
				checkDeclaredByteCount(state, rawLineCursor, "run code (C2)", r.byteLength(), r.codeLines().size());
				yield 1 + r.codeLines().size();
			}
			case ArdsInstruction.ScratchSpace _ -> {
				state.offsetActive = true;
				yield 1;
			}
			case ArdsInstruction.StoreOffsetToAddress s -> {
				state.addressClassifications.add(classification(index, rawLineCursor, instruction, s.address(), false));
				yield 1;
			}
			case ArdsInstruction.Counter _,
				 ArdsInstruction.CodeListEnd _,
				 ArdsInstruction.RelocateEngine _,
				 ArdsInstruction.OverrideHookAddress _,
				 ArdsInstruction.DataOperation _
					-> 1;
			default -> super.processInstruction(instruction, index, rawLineCursor, codeLines, state);
		};
	}
}
