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

import static org.assertj.core.api.Assertions.assertThat;

class ArdsAddressClassifierTest {

	@Test
	void classify_mainRamAddress_returnsMainRam() {
		assertThat(ArdsAddressClassifier.classify(0x02100000L)).isEqualTo(ArdsAddressClassifier.Region.MAIN_RAM);
	}

	@Test
	void classify_codeHandlerPatchAddress_returnsKnownCodeHandlerPatchNotMainRam() {
		assertThat(ArdsAddressClassifier.classify(0x023FE074L)).isEqualTo(ArdsAddressClassifier.Region.KNOWN_CODE_HANDLER_PATCH);
	}

	@Test
	void classify_hardwareIoAddress_returnsHardwareIo() {
		assertThat(ArdsAddressClassifier.classify(0x04000130L)).isEqualTo(ArdsAddressClassifier.Region.HARDWARE_IO);
	}

	@Test
	void classify_mirroredMainRamAddress_returnsMainRam() {
		// NDS button address legitimately guards on an address past the physical 4MB (main RAM is mirrored to 16MB)
		assertThat(ArdsAddressClassifier.classify(0x027FFFA8L)).isEqualTo(ArdsAddressClassifier.Region.MAIN_RAM);
	}

	@Test
	void classify_wifiIoAddress_returnsWifiIo() {
		// Used in Fire Emblem - Shadow Dragon's "Weapon Drop After Each Kill"
		assertThat(ArdsAddressClassifier.classify(0x04800004L)).isEqualTo(ArdsAddressClassifier.Region.WIFI_IO);
	}

	@Test
	void classify_arm7WramAddress_returnsArm7Wram() {
		// Datel's "Normal Speed (Press X)" for High School Musical - Makin' the Cut! (Europe) uses this
		// exact address through a load/add/store sequence
		assertThat(ArdsAddressClassifier.classify(0x038085C0L)).isEqualTo(ArdsAddressClassifier.Region.ARM7_WRAM);
	}

	@Test
	void classify_addressBelowMainRam_returnsImplausible() {
		assertThat(ArdsAddressClassifier.classify(0x01FFFFFFL)).isEqualTo(ArdsAddressClassifier.Region.UNMAPPED);
	}

	@Test
	void classify_addressAboveWifiIo_returnsImplausible() {
		assertThat(ArdsAddressClassifier.classify(0x05000000L)).isEqualTo(ArdsAddressClassifier.Region.UNMAPPED);
	}

	@Test
	void classify_gapBetweenHardwareIoAndWifiIo_returnsImplausible() {
		assertThat(ArdsAddressClassifier.classify(0x049719A1L)).isEqualTo(ArdsAddressClassifier.Region.UNMAPPED);
	}
}
