/*
 * Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.tractusx.edc.dataplane.tokenrefresh.core;

import org.eclipse.edc.connector.dataplane.spi.iam.DataPlaneAccessTokenService;
import org.eclipse.edc.connector.dataplane.spi.store.AccessTokenDataStore;
import org.eclipse.edc.iam.did.spi.resolution.DidPublicKeyResolver;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.security.PrivateKeyResolver;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.token.JwtGenerationService;
import org.eclipse.edc.token.spi.TokenValidationService;
import org.eclipse.tractusx.edc.dataplane.tokenrefresh.spi.DataPlaneTokenRefreshService;
import org.jetbrains.annotations.NotNull;

import java.security.PrivateKey;
import java.time.Clock;
import java.util.function.Supplier;

import static org.eclipse.edc.connector.dataplane.spi.TransferDataPlaneConfig.TOKEN_SIGNER_PRIVATE_KEY_ALIAS;
import static org.eclipse.tractusx.edc.dataplane.tokenrefresh.core.DataPlaneTokenRefreshServiceExtension.NAME;

@Extension(value = NAME)
public class DataPlaneTokenRefreshServiceExtension implements ServiceExtension {
    public static final String NAME = "DataPlane Token Refresh Service extension";
    public static final int DEFAULT_TOKEN_EXPIRY_TOLERANCE_SECONDS = 5;
    @Setting(value = "Token expiry tolerance period in seconds to allow for clock skew", defaultValue = "" + DEFAULT_TOKEN_EXPIRY_TOLERANCE_SECONDS)
    public static final String TOKEN_EXPIRY_TOLERANCE_SECONDS_PROPERTY = "edc.dataplane.api.token.expiry.tolerance";
    @Inject
    private TokenValidationService tokenValidationService;
    @Inject
    private DidPublicKeyResolver didPkResolver;
    @Inject
    private AccessTokenDataStore accessTokenDataStore;
    @Inject
    private PrivateKeyResolver privateKeyResolver;
    @Inject
    private Clock clock;
    @Inject
    private Vault vault;
    @Inject
    private TypeManager typeManager;

    private DataPlaneTokenRefreshServiceImpl tokenRefreshService;

    @Override
    public String name() {
        return NAME;
    }

    // exposes the service as access token service
    @Provider
    public DataPlaneAccessTokenService createAccessTokenService(ServiceExtensionContext context) {
        return getTokenRefreshService(context);
    }

    // exposes the service as pure refresh service
    @Provider
    public DataPlaneTokenRefreshService createRefreshTokenService(ServiceExtensionContext context) {
        return getTokenRefreshService(context);
    }

    @NotNull
    private DataPlaneTokenRefreshServiceImpl getTokenRefreshService(ServiceExtensionContext context) {
        if (tokenRefreshService == null) {
            var epsilon = context.getConfig().getInteger(TOKEN_EXPIRY_TOLERANCE_SECONDS_PROPERTY, DEFAULT_TOKEN_EXPIRY_TOLERANCE_SECONDS);
            tokenRefreshService = new DataPlaneTokenRefreshServiceImpl(clock, tokenValidationService, didPkResolver, accessTokenDataStore, new JwtGenerationService(), getPrivateKeySupplier(context), context.getMonitor(), null,
                    epsilon, vault, typeManager.getMapper());
        }
        return tokenRefreshService;
    }

    @NotNull
    private Supplier<PrivateKey> getPrivateKeySupplier(ServiceExtensionContext context) {
        return () -> {
            var alias = context.getConfig().getString(TOKEN_SIGNER_PRIVATE_KEY_ALIAS);
            return privateKeyResolver.resolvePrivateKey(alias)
                    .orElse(f -> {
                        context.getMonitor().warning("Cannot resolve private key: " + f.getFailureDetail());
                        return null;
                    });
        };
    }
}