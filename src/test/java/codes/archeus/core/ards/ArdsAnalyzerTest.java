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

class ArdsAnalyzerTest {
	private final ArdsAnalyzer analyzer = new ArdsAnalyzer();

	@Test
	void analyze_buttonHeldConditional_isNotSuspicious() {
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of("94000130 FFFB0000", "D2000000 00000000"));

		assertThat(analysis.isSuspicious()).isFalse();
		assertThat(analysis.addressClassifications())
				.extracting(AddressClassification::region)
				.containsExactly(ArdsAddressClassifier.Region.HARDWARE_IO);
	}

	@Test
	void analyze_codeHackHack4Pair_classifiesAsKnownCodeHandlerPatchNotSuspicious() {
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of(
				"023FE074 012FFF11",
				"023FE074 E3520003",
				"D2000000 00000000"
		));

		assertThat(analysis.isSuspicious()).isFalse();
		assertThat(analysis.addressClassifications())
				.extracting(AddressClassification::region)
				.containsExactly(ArdsAddressClassifier.Region.KNOWN_CODE_HANDLER_PATCH, ArdsAddressClassifier.Region.KNOWN_CODE_HANDLER_PATCH);
	}

	@Test
	void analyze_pointerGuardFollowedBySmallOffsetRelativeWrite_isNotSuspicious() {
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of(
				"62021880 00000000",
				"B2021880 00000000",
				"10000098 00000001",
				"D0000000 00000000",
				"D2000000 00000000"
		));

		assertThat(analysis.hasStructuralIssues()).isFalse();
		assertThat(analysis.suspiciousAddresses()).isEmpty();

		AddressClassification smallWrite = analysis.addressClassifications().stream()
				.filter(classification -> classification.address() == 0x98L)
				.findFirst()
				.orElseThrow();
		assertThat(smallWrite.region()).isEqualTo(ArdsAddressClassifier.Region.UNMAPPED);
		assertThat(smallWrite.offsetRelative()).isTrue();
		assertThat(smallWrite.isSuspicious()).isFalse();
	}

	@Test
	void analyze_sameSmallAddressWithNoOffsetContext_isSuspicious() {
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of("10000098 00000001", "D2000000 00000000"));

		assertThat(analysis.suspiciousAddresses()).hasSize(1);
		assertThat(analysis.suspiciousAddresses().getFirst().offsetRelative()).isFalse();
	}

	@Test
	void analyze_minionGarbageCode_flagsImplausibleAddress() {
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of("549719A1 D7B0972D"));

		assertThat(analysis.isSuspicious()).isTrue();
		assertThat(analysis.suspiciousAddresses()).hasSize(1);
		assertThat(analysis.suspiciousAddresses().getFirst().address()).isEqualTo(0x049719A1L);
		assertThat(analysis.structuralIssues()).anyMatch(issue -> issue.contains("unclosed conditional"));
	}

	@Test
	void analyze_codeEndingWithoutClosingItsOnlyConditional_reportsStructuralIssue() {
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of(
				"94000130 FCFF0000",
				"C0000000 00000017",
				"2212B13B 00000063",
				"DC000000 0000011C",
				"D3000000 00000000"
		));

		assertThat(analysis.structuralIssues()).anyMatch(issue -> issue.contains("unclosed conditional"));
		assertThat(analysis.structuralIssues()).anyMatch(issue -> issue.contains("unclosed repeat"));
	}

	@Test
	void analyze_strayEndIfWithNoOpenConditional_isTolerated() {
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of(
				"62285190 00000000",
				"B2285190 00000000",
				"10000050 00001B58",
				"D2000000 00000000",
				"D0000000 00000000"
		));

		assertThat(analysis.hasStructuralIssues()).isFalse();
	}

	@Test
	void analyze_unrecognizedDSubOpcode_reportsStructuralIssue() {
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of("DD000000 00000000"));

		assertThat(analysis.structuralIssues()).anyMatch(issue -> issue.contains("DD"));
	}

	@Test
	void analyze_repeatReopenedBeforeThePreviousOneClosed_reportsStructuralIssue() {
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of(
				"C0000000 00000002",
				"C0000000 00000003",
				"D1000000 00000000"
		));

		assertThat(analysis.structuralIssues()).anyMatch(issue -> issue.contains("already open"));
	}

	@Test
	void analyze_nestedInvalidRepeat_keepsTheOuterConditionalCount() {
		// The nested C0 must not overwrite the checkpoint D1 restores to, or the final
		// unclosed-conditional count comes out wrong
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of(
				"32000000 00000005",
				"C0000000 00000002",
				"32000004 00000006",
				"C0000000 00000003",
				"D1000000 00000000"
		));

		assertThat(analysis.structuralIssues()).anyMatch(issue -> issue.contains("ends with 1 unclosed conditional"));
		assertThat(analysis.structuralIssues()).noneMatch(issue -> issue.contains("ends with 2 unclosed conditional"));
	}

	@Test
	void analyze_patchCodeAddressZero_isNeverFlagged() {
		// Backlight and anti-piracy codes that inject ARM9 code give the patch code address 0, since its payload
		// is the hook itself, not a write target
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of(
				"94000130 FCFB0000",
				"023FE074 012FFF11",
				"E0000000 00000008",
				"11111111 22222222",
				"023FE074 E3520003",
				"D2000000 00000000"
		));

		assertThat(analysis.isSuspicious()).isFalse();
		assertThat(analysis.addressClassifications())
				.noneMatch(classification -> classification.instruction() instanceof ArdsInstruction.PatchCode);
	}

	@Test
	void analyze_32BitWriteToAnAddressNotMultipleOfFour_reportsStructuralIssue() {
		// Meteos (Europe)'s "One Meteo launched dumps massive amounts of meteos on opponent": a CodeBreaker 8-bit write imported as Action Replay
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of("02211F96 00000099"));

		assertThat(analysis.structuralIssues()).containsExactly("line 1: 32-bit write to 02211F96 isn't 4-byte aligned");
	}

	@Test
	void analyze_16BitCompareAtAnOddAddress_reportsStructuralIssue() {
		// Meteos (Europe)'s "CPU1 Planet Stock Modifier"
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of(
				"92065E05 00000002",
				"2221F42C 00000002",
				"D0000000 00000000"
		));

		assertThat(analysis.structuralIssues()).containsExactly("line 1: 16-bit compare to 02065E05 isn't 2-byte aligned");
	}

	@Test
	void analyze_misalignedAccesses_reportEachWidth() {
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of(
				"12000001 00000001",
				"52000002 00000001",
				"D2000000 00000000"
		));

		assertThat(analysis.structuralIssues()).containsExactly(
				"line 1: 16-bit write to 02000001 isn't 2-byte aligned",
				"line 2: 32-bit compare to 02000002 isn't 4-byte aligned"
		);
	}

	@Test
	void analyze_alignedAccessesAndByteWrites_areNotFlagged() {
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of(
				"02000004 00000001",
				"12000002 00000001",
				"22000003 00000001",
				"94000130 FDFF0000",
				"D2000000 00000000"
		));

		assertThat(analysis.structuralIssues()).isEmpty();
	}

	@Test
	void analyze_misalignedAddressWhileAnOffsetIsActive_isNotFlagged() {
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of(
				"B2000000 00000000",
				"00000002 00000001",
				"D2000000 00000000"
		));

		assertThat(analysis.structuralIssues()).isEmpty();
	}

	@Test
	void analyze_patchCodeShortByOneLine_reportsStructuralIssue() {
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of(
				"E2100000 00000010",
				"11111111 22222222"
		));

		assertThat(analysis.structuralIssues()).anyMatch(issue -> issue.contains("declares 16 byte(s)"));
	}

	@Test
	void analyze_patchCodeShortByMoreThanOneLine_reportsStructuralIssue() {
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of(
				"E2100000 00000018",
				"11111111 22222222"
		));

		assertThat(analysis.structuralIssues()).anyMatch(issue -> issue.contains("declares 24 byte(s)"));
	}

	@Test
	void analyze_patchCodeWithImplausiblyLargeByteCount_reportsStructuralIssue() {
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of("E2100000 E5A00001"));

		assertThat(analysis.structuralIssues()).anyMatch(issue -> issue.contains("implausible"));
	}

	@Test
	void analyze_patchCodeWithDuplicatedTrailingDataLine_reportsStructuralIssue() {
		// Captured from Blazer Drive (Japan)'s "Open Drive Skill Equipment Page AT.DF 999 (Shirou Status)"
		// had its 5th and final data line duplicated as a stray 6th line
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of(
				"E2100000 00000028",
				"11111111 22222222",
				"33333333 44444444",
				"55555555 66666666",
				"77777777 88888888",
				"99999999 AAAAAAAA",
				"99999999 AAAAAAAA",
				"D2000000 00000000"
		));

		assertThat(analysis.structuralIssues()).anyMatch(issue -> issue.contains("duplicates its last data line"));
	}

	@Test
	void analyze_addressAfterPatchCode_rawLineDivergesFromInstructionIndex() {
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of(
				"E2100000 00000008",
				"11111111 22222222",
				"94000130 FFFB0000",
				"D2000000 00000000"
		));

		AddressClassification conditional = analysis.addressClassifications().stream()
				.filter(classification -> classification.instruction() instanceof ArdsInstruction.Conditional16Masked)
				.findFirst()
				.orElseThrow();

		assertThat(conditional.instructionIndex()).isOne();
		assertThat(conditional.rawLine()).isEqualTo(2);
	}

	@Test
	void analyze_memoryCopyDestination_isNeverExcusedByActiveOffsetContext() {
		ArdsCodeAnalysis analysis = analyzer.analyze(List.of(
				"B2021880 00000000",
				"F0000098 00000010",
				"D2000000 00000000"
		));

		AddressClassification memoryCopyClassification = analysis.addressClassifications().stream()
				.filter(classification -> classification.instruction() instanceof ArdsInstruction.MemoryCopy)
				.findFirst()
				.orElseThrow();

		assertThat(memoryCopyClassification.offsetRelative()).isFalse();
		assertThat(memoryCopyClassification.isSuspicious()).isTrue();
	}
}
