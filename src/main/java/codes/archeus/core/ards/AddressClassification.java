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

/**
 * The memory region an instruction's address falls in.
 *
 * @param instructionIndex the instruction's position among the decoded instructions
 * @param rawLine          its line in the code, counting from 0, which is later than {@code instructionIndex} after an
 *                         instruction that takes several lines
 * @param offsetRelative   whether an offset was active, which makes an odd address expected
 */
public record AddressClassification(int instructionIndex, int rawLine, ArdsInstruction instruction, long address, ArdsAddressClassifier.Region region, boolean offsetRelative) {

	/**
	 * Whether the address is in no known region with no offset active to explain it.
	 */
	public boolean isSuspicious() {
		return region == ArdsAddressClassifier.Region.UNMAPPED && !offsetRelative;
	}
}
