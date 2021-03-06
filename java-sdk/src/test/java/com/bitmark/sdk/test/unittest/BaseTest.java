package com.bitmark.sdk.test.unittest;

import com.bitmark.apiservice.configuration.GlobalConfiguration;
import com.bitmark.apiservice.configuration.Network;
import org.junit.jupiter.api.BeforeAll;
import com.bitmark.sdk.features.BitmarkSDK;

/**
 * @author Hieu Pham
 * @since 9/12/18
 * Email: hieupham@bitmark.com
 * Copyright © 2018 Bitmark. All rights reserved.
 */

public abstract class BaseTest {

    protected static final Network NETWORK = Network.TEST_NET;

    @BeforeAll
    public static void beforeAll() {
        if (!BitmarkSDK.isInitialized())
            BitmarkSDK.init(GlobalConfiguration.builder().withApiToken("bmk-lljpzkhqdkzmblhg").withNetwork(NETWORK));

    }

}
