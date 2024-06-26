/********************************************************************************
 * Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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
 ********************************************************************************/

package org.eclipse.tractusx.edc.tests.transfer;

import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.iam.identitytrust.sts.embedded.EmbeddedSecureTokenService;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.token.JwtGenerationService;
import org.eclipse.edc.token.spi.TokenGenerationService;
import org.eclipse.tractusx.edc.tests.transfer.iatp.dispatchers.DimDispatcher;
import org.eclipse.tractusx.edc.tests.transfer.iatp.harness.IatpParticipant;
import org.eclipse.tractusx.edc.tests.transfer.iatp.runtime.IatpParticipantRuntime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpResponse;

import java.io.IOException;
import java.security.PrivateKey;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.eclipse.tractusx.edc.tests.transfer.iatp.harness.IatpHelperFunctions.configureParticipant;
import static org.eclipse.tractusx.edc.tests.transfer.iatp.runtime.Runtimes.dimRuntime;
import static org.mockserver.model.HttpRequest.request;

@EndToEndTest
public class DimHttpConsumerPullTest extends AbstractIatpConsumerPullTest {

    @RegisterExtension
    protected static final IatpParticipantRuntime CONSUMER_RUNTIME = dimRuntime(CONSUMER.getName(), CONSUMER.iatpConfiguration(PROVIDER), CONSUMER.getKeyPair());
    @RegisterExtension
    protected static final IatpParticipantRuntime PROVIDER_RUNTIME = dimRuntime(PROVIDER.getName(), PROVIDER.iatpConfiguration(CONSUMER), PROVIDER.getKeyPair());
    private static final TypeManager MAPPER = new JacksonTypeManager();
    private static ClientAndServer oauthServer;
    private static ClientAndServer dimServer;

    @BeforeAll
    static void prepare() {

        var tokenGeneration = new JwtGenerationService();

        var generatorServices = Map.of(
                CONSUMER.getDid(), tokenServiceFor(tokenGeneration, CONSUMER),
                PROVIDER.getDid(), tokenServiceFor(tokenGeneration, PROVIDER));

        oauthServer = ClientAndServer.startClientAndServer(STS.stsUri().getPort());

        oauthServer.when(request().withMethod("POST").withPath(STS.stsUri().getPath() + "/token"))
                .respond(HttpResponse.response(MAPPER.writeValueAsString(Map.of("access_token", "token"))));

        dimServer = ClientAndServer.startClientAndServer(DIM_URI.getPort());
        dimServer.when(request().withMethod("POST")).respond(new DimDispatcher(generatorServices));

        // create the DIDs cache
        var dids = new HashMap<String, DidDocument>();
        dids.put(DATASPACE_ISSUER_PARTICIPANT.didUrl(), DATASPACE_ISSUER_PARTICIPANT.didDocument());
        dids.put(CONSUMER.getDid(), CONSUMER.getDidDocument());
        dids.put(PROVIDER.getDid(), PROVIDER.getDidDocument());

        configureParticipant(DATASPACE_ISSUER_PARTICIPANT, CONSUMER, CONSUMER_RUNTIME, dids, null);
        configureParticipant(DATASPACE_ISSUER_PARTICIPANT, PROVIDER, PROVIDER_RUNTIME, dids, null);

    }

    @AfterAll
    static void unwind() throws IOException {
        oauthServer.stop();
        dimServer.stop();
    }

    private static EmbeddedSecureTokenService tokenServiceFor(TokenGenerationService tokenGenerationService, IatpParticipant iatpDimParticipant) {
        return new EmbeddedSecureTokenService(tokenGenerationService, privateKeySupplier(iatpDimParticipant), publicIdSupplier(iatpDimParticipant), Clock.systemUTC(), 60 * 60);
    }

    private static Supplier<PrivateKey> privateKeySupplier(IatpParticipant participant) {
        return () -> participant.getKeyPair().getPrivate();
    }

    private static Supplier<String> publicIdSupplier(IatpParticipant participant) {
        return participant::verificationId;
    }

    @Override
    protected IatpParticipantRuntime consumerRuntime() {
        return CONSUMER_RUNTIME;
    }

    @Override
    protected IatpParticipantRuntime providerRuntime() {
        return CONSUMER_RUNTIME;
    }
}
