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

class NitrohaxAnalyzerTest {
	private final NitrohaxAnalyzer analyzer = new NitrohaxAnalyzer();

	@Test
	void analyze_realBlueDragonCode_isNoLongerFlaggedOnceC6IsRecognized() {
		// The lines of Blue Dragon - Awakened Shadow (Europe)'s "Max Cash After Use V2". ArdsAnalyzer flags them,
		// since C6 is a Nitrohax opcode.
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of(
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

		assertThat(analysis.isSuspicious()).isFalse();
	}

	@Test
	void analyze_storeOffsetToAddress_isNeverExcusedByActiveOffsetContext() {
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of(
				"B2021880 00000000",
				"C6000000 00000098",
				"D2000000 00000000"
		));

		AddressClassification storeOffsetClassification = analysis.addressClassifications().stream()
				.filter(classification -> classification.instruction() instanceof ArdsInstruction.StoreOffsetToAddress)
				.findFirst()
				.orElseThrow();

		assertThat(storeOffsetClassification.offsetRelative()).isFalse();
		assertThat(storeOffsetClassification.isSuspicious()).isTrue();
	}

	@Test
	void analyze_callFunction_checksFunctionAddress() {
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of("C1000000 67238071"));

		assertThat(analysis.suspiciousAddresses()).hasSize(1);
		assertThat(analysis.suspiciousAddresses().getFirst().address()).isEqualTo(0x67238071L);
	}

	@Test
	void analyze_runCodeShortByOneLine_reportsStructuralIssue() {
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of(
				"C2000000 00000010",
				"AAAAAAAA BBBBBBBB"
		));

		assertThat(analysis.structuralIssues()).anyMatch(issue -> issue.contains("run code (C2) declares 16 byte(s)"));
	}

	@Test
	void analyze_runCodeShortByMoreThanOneLine_reportsStructuralIssue() {
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of(
				"C2000000 00000018",
				"AAAAAAAA BBBBBBBB"
		));

		assertThat(analysis.structuralIssues()).anyMatch(issue -> issue.contains("run code (C2) declares 24 byte(s)"));
	}

	@Test
	void analyze_scratchSpace_setsOffsetActiveLikeLoadOffset() {
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of(
				"C4000000 00000000",
				"10000098 00000001",
				"D2000000 00000000"
		));

		assertThat(analysis.suspiciousAddresses()).isEmpty();
	}

	@Test
	void analyze_vanillaCodeThroughNitrohaxAnalyzer_behavesIdenticallyToArdsAnalyzer() {
		List<String> buttonHeldConditional = List.of("94000130 FFFB0000", "D2000000 00000000");

		assertThat(analyzer.analyze(buttonHeldConditional).isSuspicious())
				.isEqualTo(new ArdsAnalyzer().analyze(buttonHeldConditional).isSuspicious())
				.isFalse();
	}
}
