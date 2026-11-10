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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NitrohaxParserTest {
	private final NitrohaxParser parser = new NitrohaxParser();

	@Test
	void parse_callFunction_readsAheadPackedArguments() {
		// Exact example from the Nitrohax README
		List<ArdsInstruction> instructions = parser.parse(List.of(
				"C1000003 02049A48",
				"00000010 134CBA9C",
				"12345678 00000000"
		));

		assertThat(instructions).containsExactly(new ArdsInstruction.CallFunction(3, 0x02049A48L, List.of("00000010 134CBA9C", "12345678 00000000")));
	}

	@Test
	void parse_callFunctionWithZeroArgs_readsNoMoreLines() {
		List<ArdsInstruction> instructions = parser.parse(List.of(
				"C1000000 02049A48",
				"02100000 00000001"
		));

		assertThat(instructions).containsExactly(
				new ArdsInstruction.CallFunction(0, 0x02049A48L, List.of()),
				new ArdsInstruction.ConstantWrite(32, 0x02100000L, 1L)
		);
	}

	@Test
	void parse_runCodeInArmMode_readsTheDeclaredByteCountOfLines() {
		// Exact example from the Nitrohax README
		List<ArdsInstruction> instructions = parser.parse(List.of(
				"C2000000 00000010",
				"AAAAAAAA BBBBBBBB",
				"CCCCCCCC E12FFF1E"
		));

		assertThat(instructions).containsExactly(new ArdsInstruction.RunCode(0, 16L, List.of("AAAAAAAA BBBBBBBB", "CCCCCCCC E12FFF1E")));
	}

	@Test
	void parse_runCodeInThumbMode_readsTheModeFromTheLowNibble() {
		List<ArdsInstruction> instructions = parser.parse(List.of(
				"C2000001 00000008",
				"AAAAAAAA BBBBBBBB"
		));

		assertThat(instructions).containsExactly(new ArdsInstruction.RunCode(1, 8L, List.of("AAAAAAAA BBBBBBBB")));
	}

	@Test
	void parse_scratchSpace_returnsScratchSpace() {
		List<ArdsInstruction> instructions = parser.parse(List.of("C4000000 12345678"));

		assertThat(instructions).containsExactly(new ArdsInstruction.ScratchSpace(0x12345678L));
	}

	@Test
	void parse_counter_splitsCompareValueAndMask() {
		List<ArdsInstruction> instructions = parser.parse(List.of("C5000000 00AB00FF"));

		assertThat(instructions).containsExactly(new ArdsInstruction.Counter(0x00AB, 0x00FF));
	}

	@Test
	void parse_realBlueDragonStoreOffset_returnsStoreOffsetToAddress() {
		// A line from Blue Dragon - Awakened Shadow (Europe)'s "Max Cash After Use V2"
		List<ArdsInstruction> instructions = parser.parse(List.of("C6000000 02000804"));

		assertThat(instructions).containsExactly(new ArdsInstruction.StoreOffsetToAddress(0x02000804L));
	}

	@Test
	void parse_cfFamily_decodesAllThreeSubOpcodes() {
		List<ArdsInstruction> instructions = parser.parse(List.of(
				"CF000000 00000000",
				"CF000001 02100000",
				"CF000002 02100004"
		));

		assertThat(instructions).containsExactly(
				new ArdsInstruction.CodeListEnd(),
				new ArdsInstruction.RelocateEngine(0x02100000L),
				new ArdsInstruction.OverrideHookAddress(0x02100004L)
		);
	}

	@Test
	void parse_unknownCfType_returnsUnknown() {
		List<ArdsInstruction> instructions = parser.parse(List.of("CF000003 00000000"));

		assertThat(instructions).hasSize(1);
		assertThat(instructions.getFirst()).isInstanceOf(ArdsInstruction.Unknown.class);
		// address already carries the leading F nibble ("F000003"), so the message must not double it
		assertThat(((ArdsInstruction.Unknown) instructions.getFirst()).reason())
				.isEqualTo("unrecognized Nitrohax CF sub-opcode \"CF000003\"");
	}

	@Test
	void parse_dataOperation_decodesEveryOperationSelector() {
		List<ArdsInstruction> instructions = parser.parse(List.of(
				"D4000000 00000005",
				"D4000001 00000005",
				"D4000002 00000005",
				"D4000003 00000005",
				"D4000004 00000005",
				"D4000005 00000005",
				"D4000006 00000005",
				"D4000007 00000005",
				"D4000008 00000005"
		));

		assertThat(instructions).containsExactly(
				new ArdsInstruction.DataOperation(DataOperator.ADD, 5L),
				new ArdsInstruction.DataOperation(DataOperator.OR, 5L),
				new ArdsInstruction.DataOperation(DataOperator.AND, 5L),
				new ArdsInstruction.DataOperation(DataOperator.XOR, 5L),
				new ArdsInstruction.DataOperation(DataOperator.SHIFT_LEFT, 5L),
				new ArdsInstruction.DataOperation(DataOperator.SHIFT_RIGHT, 5L),
				new ArdsInstruction.DataOperation(DataOperator.ROTATE_RIGHT, 5L),
				new ArdsInstruction.DataOperation(DataOperator.ARITHMETIC_SHIFT_RIGHT, 5L),
				new ArdsInstruction.DataOperation(DataOperator.MULTIPLY, 5L)
		);
	}

	@Test
	void parse_dataOperationWithUnknownSelector_returnsUnknown() {
		List<ArdsInstruction> instructions = parser.parse(List.of("D4000009 00000005"));

		assertThat(instructions).hasSize(1);
		assertThat(instructions.getFirst()).isInstanceOf(ArdsInstruction.Unknown.class);
	}

	@Test
	void parse_vanillaRepeat_stillDecodesThroughSuper() {
		List<ArdsInstruction> instructions = parser.parse(List.of("C0000000 00000005"));

		assertThat(instructions).containsExactly(new ArdsInstruction.Repeat(5L));
	}

	@Test
	void parse_unrecognizedCSubOpcode_stillReturnsUnknown() {
		List<ArdsInstruction> instructions = parser.parse(List.of("C3000000 00000000"));

		assertThat(instructions).hasSize(1);
		assertThat(instructions.getFirst()).isInstanceOf(ArdsInstruction.Unknown.class);
	}

	@Test
	void parse_realBlueDragonCode_decodesEveryLineWithoutUnknown() {
		// The lines of Blue Dragon - Awakened Shadow (Europe)'s "Max Cash After Use V2"
		List<ArdsInstruction> instructions = parser.parse(List.of(
				"52000804 E58CC208",
				"02000804 021B0000",
				"D2000000 00000000",
				"82000806 0000021F",
				"02000804 021B0000",
				"D2000000 00000000",
				"94000130 FFFB0000",
				"62000804 00000000",
				"B2000804 00000000",
				"C0000000 00001000",
				"50000000 C1A04000",
				"00000000 E59F4028",
				"D0000000 00000000",
				"DC000000 00000004",
				"C6000000 02000804",
				"D2000000 00000000"
		));

		assertThat(instructions).noneMatch(instruction -> instruction instanceof ArdsInstruction.Unknown);
		assertThat(instructions.get(14)).isEqualTo(new ArdsInstruction.StoreOffsetToAddress(0x02000804L));
	}
}
