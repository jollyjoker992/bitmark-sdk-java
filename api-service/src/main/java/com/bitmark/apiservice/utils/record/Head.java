package com.bitmark.apiservice.utils.record;

import com.google.gson.annotations.SerializedName;

public enum Head {

    @SerializedName("head")
    HEAD,

    @SerializedName("moved")
    MOVED,

    @SerializedName("prior")
    PRIOR
}
