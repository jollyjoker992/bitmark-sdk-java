/**
 * SPDX-License-Identifier: ISC
 * Copyright © 2014-2019 Bitmark. All rights reserved.
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 */
package com.bitmark.apiservice.params;

import com.bitmark.cryptography.crypto.key.KeyPair;

import java.util.List;

public interface MultipleParams extends Params {

    List<byte[]> sign(KeyPair key);
}
