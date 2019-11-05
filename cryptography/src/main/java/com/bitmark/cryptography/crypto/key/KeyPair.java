/**
 * SPDX-License-Identifier: ISC
 * Copyright © 2014-2019 Bitmark. All rights reserved.
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 */
package com.bitmark.cryptography.crypto.key;

import com.bitmark.cryptography.utils.Validation;

public interface KeyPair extends Validation {

    PublicKey publicKey();

    PrivateKey privateKey();

}
