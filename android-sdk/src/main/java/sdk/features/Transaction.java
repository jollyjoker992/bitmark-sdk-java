package sdk.features;

import apiservice.ApiService;
import apiservice.params.query.TransactionQueryBuilder;
import apiservice.response.GetTransactionResponse;
import apiservice.response.GetTransactionsResponse;
import apiservice.utils.callback.Callback1;

import static sdk.utils.CommonUtils.wrapCallbackOnMain;

/**
 * @author Hieu Pham
 * @since 8/31/18
 * Email: hieupham@bitmark.com
 * Copyright © 2018 Bitmark. All rights reserved.
 */
public class Transaction {

    public static void get(String txId, Callback1<GetTransactionResponse> callback) {
        get(txId, false, wrapCallbackOnMain(callback));
    }

    public static void get(String txId, boolean loadAsset,
                           Callback1<GetTransactionResponse> callback) {
        ApiService.getInstance().getTransaction(txId, loadAsset, wrapCallbackOnMain(callback));
    }

    public static void list(TransactionQueryBuilder builder,
                            Callback1<GetTransactionsResponse> callback) {
        ApiService.getInstance().listTransactions(builder.build(), wrapCallbackOnMain(callback));
    }
}
