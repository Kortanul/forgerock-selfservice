/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2015 ForgeRock AS.
 */

package org.forgerock.selfservice.example;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.json.resource.Requests.newCreateRequest;
import static org.forgerock.json.resource.Resources.newInternalConnectionFactory;

import org.forgerock.http.Context;
import org.forgerock.http.context.RootContext;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.Connection;
import org.forgerock.json.resource.ConnectionFactory;
import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.MemoryBackend;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.json.resource.Responses;
import org.forgerock.json.resource.Router;
import org.forgerock.json.resource.SingletonResourceProvider;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * Initialises CREST services.
 *
 * @since 0.1.0
 */
final class CrestServiceRegister {

    ConnectionFactory initialise(Properties properties) throws ResourceException {
        Router router = new Router();
        router.addRoute(Router.uriTemplate("/users"), new MemoryBackend());
        router.addRoute(Router.uriTemplate("/email"), new EmailService(properties));

        ConnectionFactory connectionFactory = newInternalConnectionFactory(router);
        createDemoData(connectionFactory);
        return connectionFactory;
    }

    private void createDemoData(ConnectionFactory connectionFactory) throws ResourceException {
        Connection connection = connectionFactory.getConnection();
        connection.create(new RootContext(),
                newCreateRequest("/users", "andy123", buildUser("Andy", "andrew.forrest@forgerock.com")));
        connection.create(new RootContext(),
                newCreateRequest("/users", "jake123", buildUser("Jake", "jake.feasel@forgerock.com")));
        connection.create(new RootContext(),
                newCreateRequest("/users", "andi123", buildUser("Andi", "andi.egloff@forgerock.com")));
    }

    private JsonValue buildUser(String name, String email) {
        return json(
                object(
                        field("name", name),
                        field("mail", email),
                        field("_rev", "1.0")));
    }

    private static final class EmailService implements SingletonResourceProvider {

        private final Properties properties;

        EmailService(Properties properties) {
            this.properties = properties;
        }

        @Override
        public Promise<ActionResponse, ResourceException> actionInstance(Context context, ActionRequest request) {
            if (request.getAction().equals("send")) {
                try {
                    JsonValue response = sendEmail(request.getContent());
                    return Promises.newResultPromise(Responses.newActionResponse(response));
                } catch (ResourceException rE) {
                    return Promises.newExceptionPromise(rE);
                }
            }

            return Promises.newExceptionPromise(
                    ResourceException.newNotSupportedException("Unknown action " + request.getAction()));
        }

        private JsonValue sendEmail(JsonValue document) throws ResourceException {
            String to = document.get("to").asString();

            if (isEmpty(to)) {
                throw new BadRequestException("Field to is not specified");
            }

            String from = document.get("from").asString();

            if (isEmpty(from)) {
                throw new BadRequestException("Field from is not specified");
            }

            String subject = document.get("subject").asString();

            if (isEmpty(subject)) {
                throw new BadRequestException("Field subject is not specified");
            }

            String messageBody = document.get("message").asString();

            if (isEmpty(messageBody)) {
                throw new BadRequestException("Field message is not specified");
            }

            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", properties.getProperty("emailserver.host"));
            props.put("mail.smtp.port", properties.getProperty("emailserver.port"));

            Session session = Session.getInstance(props, new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(
                            properties.getProperty("emailserver.username"),
                            properties.getProperty("emailserver.password"));
                }
            });

            try {
                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(from));
                message.setRecipients(Message.RecipientType.TO,
                        InternetAddress.parse(to));
                message.setSubject(subject);
                message.setText(messageBody);

                Transport.send(message);
            } catch (MessagingException mE) {
                throw new InternalServerErrorException(mE);
            }

            return json(object(field("status", "okay")));
        }

        @Override
        public Promise<ResourceResponse, ResourceException> patchInstance(Context context, PatchRequest request) {
            return Promises.newExceptionPromise(ResourceException.newNotSupportedException());
        }

        @Override
        public Promise<ResourceResponse, ResourceException> readInstance(Context context, ReadRequest request) {
            return Promises.newExceptionPromise(ResourceException.newNotSupportedException());
        }

        @Override
        public Promise<ResourceResponse, ResourceException> updateInstance(Context context, UpdateRequest request) {
            return Promises.newExceptionPromise(ResourceException.newNotSupportedException());
        }

    }

}