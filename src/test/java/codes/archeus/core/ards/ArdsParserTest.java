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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ArdsParserTest {
	private final ArdsParser parser = new ArdsParser();

	@Test
	void parse_type0_returns32BitConstantWrite() {
		List<ArdsInstruction> instructions = parser.parse(List.of("02100000 12345678"));

		assertThat(instructions).containsExactly(new ArdsInstruction.ConstantWrite(32, 0x02100000L, 0x12345678L));
	}

	@Test
	void parse_type1_returns16BitConstantWriteMaskedToHalfword() {
		List<ArdsInstruction> instructions = parser.parse(List.of("12100000 0000ABCD"));

		assertThat(instructions).containsExactly(new ArdsInstruction.ConstantWrite(16, 0x02100000L, 0xABCDL));
	}

	@Test
	void parse_type2_returns8BitConstantWriteMaskedToByte() {
		List<ArdsInstruction> instructions = parser.parse(List.of("22100000 000000EF"));

		assertThat(instructions).containsExactly(new ArdsInstruction.ConstantWrite(8, 0x02100000L, 0xEFL));
	}

	@Test
	void parse_type6_returnsNotEqualConditional32() {
		List<ArdsInstruction> instructions = parser.parse(List.of("62021880 00000000"));

		assertThat(instructions).containsExactly(new ArdsInstruction.Conditional32(ConditionOperator.NOT_EQUAL, 0x02021880L, 0L));
	}

	@Test
	void parse_typeB_returnsLoadOffset() {
		List<ArdsInstruction> instructions = parser.parse(List.of("B2021880 00000000"));

		assertThat(instructions).containsExactly(new ArdsInstruction.LoadOffset(0x02021880L));
	}

	@Test
	void parse_type9_returnsEqualConditional16MaskedWithInvertedMaskSemantics() {
		List<ArdsInstruction> instructions = parser.parse(List.of("94000130 FFFB0000"));

		assertThat(instructions).containsExactly(new ArdsInstruction.Conditional16Masked(ConditionOperator.EQUAL, 0x04000130L, 0xFFFB, 0x0000));
	}

	@Test
	void parse_typeCWithZeroSubOpcode_returnsRepeat() {
		List<ArdsInstruction> instructions = parser.parse(List.of("C0000000 00000005"));

		assertThat(instructions).containsExactly(new ArdsInstruction.Repeat(5L));
	}

	@Test
	void parse_typeCWithNonZeroSubOpcode_returnsUnknown() {
		List<ArdsInstruction> instructions = parser.parse(List.of("C0000001 00000005"));

		assertThat(instructions).hasSize(1);
		assertThat(instructions.getFirst()).isInstanceOf(ArdsInstruction.Unknown.class);
	}

	@Test
	void parse_dFamily_decodesEverySubOpcode() {
		List<ArdsInstruction> instructions = parser.parse(List.of(
				"D0000000 00000000",
				"D1000000 00000000",
				"D2000000 00000000",
				"D3000000 02100000",
				"D4000000 00000001",
				"D5000000 0000000A",
				"D6000000 02100000",
				"D7000000 02100000",
				"D8000000 02100000",
				"D9000000 02100000",
				"DA000000 02100000",
				"DB000000 02100000",
				"DC000000 00000010"
		));

		assertThat(instructions).containsExactly(
				new ArdsInstruction.EndIf(),
				new ArdsInstruction.EndRepeat(),
				new ArdsInstruction.EndCode(),
				new ArdsInstruction.SetOffset(0x02100000L),
				new ArdsInstruction.AddToStored(1L),
				new ArdsInstruction.SetStored(10L),
				new ArdsInstruction.StoreAndIncrement(32, 0x02100000L),
				new ArdsInstruction.StoreAndIncrement(16, 0x02100000L),
				new ArdsInstruction.StoreAndIncrement(8, 0x02100000L),
				new ArdsInstruction.LoadStored(32, 0x02100000L),
				new ArdsInstruction.LoadStored(16, 0x02100000L),
				new ArdsInstruction.LoadStored(8, 0x02100000L),
				new ArdsInstruction.AddToOffset(0x10L)
		);
	}

	@Test
	void parse_unknownDType_returnsUnknown() {
		List<ArdsInstruction> instructions = parser.parse(List.of("DD000000 00000000"));

		assertThat(instructions).hasSize(1);
		assertThat(instructions.getFirst()).isInstanceOf(ArdsInstruction.Unknown.class);
		assertThat(((ArdsInstruction.Unknown) instructions.getFirst()).reason()).contains("DD");
	}

	@Test
	void parse_typeE_readsAheadDeclaredByteCountOfDataLines() {
		List<ArdsInstruction> instructions = parser.parse(List.of(
				"E2100000 00000010",
				"11111111 22222222",
				"33333333 44444444",
				"02200000 00000001"
		));

		assertThat(instructions).containsExactly(
				new ArdsInstruction.PatchCode(0x02100000L, 16L, List.of("11111111 22222222", "33333333 44444444")),
				new ArdsInstruction.ConstantWrite(32, 0x02200000L, 1L)
		);
	}

	@Test
	void parse_typeEWithFewerLinesThanDeclared_collectsWhatRemains() {
		List<ArdsInstruction> instructions = parser.parse(List.of(
				"E2100000 00000010",
				"11111111 22222222"
		));

		assertThat(instructions).containsExactly(
				new ArdsInstruction.PatchCode(0x02100000L, 16L, List.of("11111111 22222222"))
		);
	}

	@Test
	void parse_typeF_returnsMemoryCopy() {
		List<ArdsInstruction> instructions = parser.parse(List.of("F0200000 00000020"));

		assertThat(instructions).containsExactly(new ArdsInstruction.MemoryCopy(0x00200000L, 32L));
	}

	@Test
	void parse_malformedLine_returnsUnknown() {
		List<ArdsInstruction> instructions = parser.parse(List.of("not a hex pair"));

		assertThat(instructions).hasSize(1);
		assertThat(instructions.getFirst()).isInstanceOf(ArdsInstruction.Unknown.class);
	}
}
