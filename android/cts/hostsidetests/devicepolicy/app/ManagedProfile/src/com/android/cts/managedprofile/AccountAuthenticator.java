/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.cts.managedprofile;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.os.Bundle;

/* package */ class AccountAuthenticator extends AbstractAccountAuthenticator {
    public static final String ACCOUNT_NAME = "CTS";
    public static final String ACCOUNT_TYPE = "com.android.cts.test";

    private static AccountAuthenticator sAuthenticator = null;
    private static final String AUTH_TOKEN = "authToken";
    private static final String AUTH_TOKEN_LABEL = "authTokenLabel";

    private AccountAuthenticator(Context context) {
        super(context);
    }

    private Bundle createResultBundle() {
        Bundle result = new Bundle();
        result.putString(AccountManager.KEY_ACCOUNT_NAME, ACCOUNT_NAME);
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, ACCOUNT_TYPE);
        result.putString(AccountManager.KEY_AUTHTOKEN, AUTH_TOKEN);

        return result;
    }

    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response, String accountType,
            String authTokenType, String[] requiredFeatures, Bundle options)
            throws NetworkErrorException {
        return createResultBundle();
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
        return createResultBundle();
    }

    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account,
            String authTokenType, Bundle options) throws NetworkErrorException {
        return createResultBundle();
    }

    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account,
            Bundle options) throws NetworkErrorException {

        Bundle result = new Bundle();
        result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true);
        return result;
    }

    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account,
            String authTokenType, Bundle options) throws NetworkErrorException {
        return createResultBundle();
    }

    @Override
    public String getAuthTokenLabel(String authTokenType) {
        return AUTH_TOKEN_LABEL;
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account,
            String[] features) throws NetworkErrorException {

        Bundle result = new Bundle();
        result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true);
        return result;
    }

    public static synchronized AccountAuthenticator getAuthenticator(Context context) {
        if (sAuthenticator == null) {
            sAuthenticator = new AccountAuthenticator(context.getApplicationContext());
        }
        return sAuthenticator;
    }
}
