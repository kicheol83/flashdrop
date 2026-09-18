package com.flashdrop.strategy.redis;

final class RedisKeys {

    private RedisKeys() {
    }

    static String stock(String campaignCode) {
        return "flashdrop:campaign:" + campaignCode + ":stock";
    }

    static String claimed(String campaignCode) {
        return "flashdrop:campaign:" + campaignCode + ":claimed";
    }

    static String campaignId(String campaignCode) {
        return "flashdrop:campaign:" + campaignCode + ":campaignId";
    }

    static String startsAt(String campaignCode) {
        return "flashdrop:campaign:" + campaignCode + ":startsAt";
    }
}
