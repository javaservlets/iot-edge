/*
 * Copyright 2016-2020 ForgeRock AS. All Rights Reserved
 *
 * Use of this code requires a commercial software license with ForgeRock AS.
 * or with one of its affiliates. All use shall be exclusively subject
 * to such license between the licensee and ForgeRock AS.
 */

package org.forgerock.openicf.connectors.google;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.cloudiot.v1.CloudIot;
import com.google.api.services.cloudiot.v1.CloudIotScopes;
import com.google.api.services.cloudiot.v1.model.Device;
import com.google.api.services.cloudiot.v1.model.DeviceConfig;
import com.google.api.services.cloudiot.v1.model.ModifyCloudToDeviceConfigRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.identityconnectors.framework.common.exceptions.ConnectorSecurityException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.*;
import org.identityconnectors.framework.spi.operations.*;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Main implementation of the Google Cloud Platform IoT Core Connector.
 */
@ConnectorClass(displayNameKey = "GCPIoTCore.connector.display", configurationClass = GCPIoTCoreConfiguration.class)
public class GCPIoTCoreConnector implements Connector, TestOp, SchemaOp, SearchOp<Filter>, SyncOp, UpdateOp {
    private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS`Z`");
    private static final AttributeInfo THING_TYPE_ATTR_INFO = AttributeInfoBuilder.build("thingType", String.class);
    private static final AttributeInfo STATUS_ATTR_INFO = AttributeInfoBuilder.build("accountStatus", String.class);
    private static final AttributeInfo THING_CONFIG_ATTR_INFO = AttributeInfoBuilder.build("thingConfig", String.class);
    private static final AttributeInfo UID_ATTR_INFO = AttributeInfoBuilder.build(Uid.NAME, String.class);
    private static final Attribute THING_TYPE_ATTR = AttributeBuilder.build("thingType", "DEVICE");
    private static final ObjectClass THINGS = new ObjectClass("THINGS");
    private static final Log logger = Log.getLog(GCPIoTCoreConnector.class);

    private GCPIoTCoreConfiguration configuration;
    private Schema schema = null;

    private CloudIot getService() throws ConnectorIOException, ConnectorSecurityException {
        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(this.configuration.getCredentialsAsStream())
                    .createScoped(CloudIotScopes.all());
            credentials.refreshIfExpired();
            CloudIot.Builder builder = new CloudIot.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JacksonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials));
            builder.setApplicationName("idm-connector");
            return builder.build();
        } catch (IOException e) {
            throw new ConnectorIOException("Cloud IoT service", e);
        } catch (GeneralSecurityException e) {
            throw new ConnectorSecurityException("Cloud IoT service", e);
        }
    }

    private String getRegistryPath() {
        return String.format("projects/%1$s/locations/%2$s/registries/%3$s",
                this.configuration.getProjectId(),
                this.configuration.getRegion(),
                this.configuration.getRegistryId());
    }

    private String getDevicePath(String name) {
        return String.format("%s/devices/%s", getRegistryPath(), name);
    }

    private List<Device> getDevices(CloudIot service) throws ConnectorIOException {
        try {
            return service.projects()
                    .locations()
                    .registries()
                    .devices()
                    .list(getRegistryPath())
                    .setFieldMask("(blocked,config)")
                    .execute()
                    .getDevices();
        } catch (IOException e) {
            logger.error("Device list failed", e);
            throw new ConnectorIOException("Device list failed", e);
        }
    }

    @Override
    public Configuration getConfiguration() {
        return this.configuration;
    }

    @Override
    public void init(final Configuration configuration) {
        this.configuration = (GCPIoTCoreConfiguration) configuration;
    }

    @Override
    public void dispose() {
        configuration = null;
    }

    /******************
     * SPI Operations
     *
     * Implement the following operations using the contract and
     * description found in the Javadoc for these methods.
     ******************/

    @Override
    public void sync(ObjectClass objectClass, SyncToken syncToken, SyncResultsHandler handler,
            OperationOptions operationOptions) {
        isThing(objectClass);
        CloudIot service = getService();
        List<Device> devices = getDevices(service);
        for( Device d : devices ) {
            SyncDeltaBuilder deltaBuilder = new SyncDeltaBuilder();
            deltaBuilder.setObject(buildThing(d));
            deltaBuilder.setDeltaType(SyncDeltaType.CREATE_OR_UPDATE);
            deltaBuilder.setToken(syncToken);
            if (!handler.handle(deltaBuilder.build())) {
                break;
            }
        }
        ((SyncTokenResultsHandler) handler).handleResult(syncToken);
    }

    @Override
    public SyncToken getLatestSyncToken(ObjectClass objectClass) {
        isThing(objectClass);
        return new SyncToken(simpleDateFormat.format(new Date()));
    }

    @Override
    public Schema schema() {
        if (null != schema) {
            return schema;
        }
        SchemaBuilder builder = new SchemaBuilder(GCPIoTCoreConnector.class);
        ObjectClassInfoBuilder thingsInfoBuilder = new ObjectClassInfoBuilder();
        thingsInfoBuilder.setType(THINGS.getObjectClassValue());
        thingsInfoBuilder.addAttributeInfo(Name.INFO);
        thingsInfoBuilder.addAttributeInfo(UID_ATTR_INFO);
        thingsInfoBuilder.addAttributeInfo(THING_TYPE_ATTR_INFO);
        thingsInfoBuilder.addAttributeInfo(THING_CONFIG_ATTR_INFO);
        thingsInfoBuilder.addAttributeInfo(STATUS_ATTR_INFO);
        builder.defineObjectClass(thingsInfoBuilder.build(), SearchOp.class, SyncOp.class);
        schema = builder.build();
        return schema;
    }

    @Override
    public FilterTranslator<Filter> createFilterTranslator(ObjectClass objectClass, OperationOptions options) {
        return new GCPIoTCoreFilterTranslator();
    }

    @Override
    public void executeQuery(ObjectClass objectClass, Filter query, ResultsHandler handler, OperationOptions options) {
        isThing(objectClass);
        CloudIot service = getService();
        List<Device> devices = getDevices(service);
        if (devices == null) {
            logger.error(String.format("empty list returned for %s", getRegistryPath()));
            return;
        }
        for( Device d : devices ) {
            ConnectorObject thing = buildThing(d);
            if ((query == null || query.accept(thing)) && !handler.handle(thing)) {
                break;
            }
        }
        ((SearchResultsHandler) handler).handleResult(new SearchResult());
    }


    @Override
    public void test() {
        // test the connection to the IoT Hub
        getService();
    }

    private ConnectorObject buildThing(Device device) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(THINGS);
        builder.setUid(device.getId());
        builder.setName(device.getId());
        builder.addAttribute(THING_TYPE_ATTR);

        // if a device is not blocked then the API returns a null even when that field is requested
        Boolean blocked = device.getBlocked();
        builder.addAttribute(AttributeBuilder.build("accountStatus",
                blocked != null && blocked ? "inactive" : "active"));

        byte[] configBytes = device.getConfig() != null ? device.getConfig().decodeBinaryData(): null;
        if (configBytes != null) {
            String config = new String(configBytes, StandardCharsets.UTF_8);
            builder.addAttribute(AttributeBuilder.build("thingConfig", config));
        }

        return builder.build();
    }

    private void isThing(ObjectClass objectClass) {
        if (!THINGS.equals(objectClass)) {
            throw new IllegalArgumentException(String.format("Operation requires ObjectClass %s, received %s",
                    THINGS.getDisplayNameKey(), objectClass));
        }
    }

    @Override
    public Uid update(ObjectClass objectClass, Uid uid, Set<Attribute> set, OperationOptions operationOptions) {
        isThing(objectClass);
        CloudIot service = getService();
        AttributesAccessor attributesAccessor = new AttributesAccessor(set);
        if( attributesAccessor.hasAttribute(STATUS_ATTR_INFO.getName())) {
            updateBlocked(service, uid, attributesAccessor.findString(STATUS_ATTR_INFO.getName()));
        }
        if( attributesAccessor.hasAttribute(THING_CONFIG_ATTR_INFO.getName())) {
            updateConfig(service, uid, attributesAccessor.findString(THING_CONFIG_ATTR_INFO.getName()));
        }
        return uid;
    }

    private void updateBlocked(CloudIot service, Uid uid, String accountStatus) throws ConnectorIOException {
        final String devicePath = getDevicePath(uid.getUidValue());
        logger.info("update blocked for {0} with account status {1}", devicePath, accountStatus);

        Device thing = new Device().setBlocked(accountStatus.equals("inactive"));
        try {
            service.projects()
                    .locations()
                    .registries()
                    .devices()
                    .patch(devicePath, thing)
                    .setUpdateMask("blocked")
                    .execute();
        } catch (IOException e) {
            logger.error("Device blocked update failed", e);
            throw new ConnectorIOException("Device blocked update failed", e);
        }
    }

    private void updateConfig(CloudIot service, Uid uid, String config) {
        final String devicePath = getDevicePath(uid.getUidValue());
        logger.info("update config of {0} to {1}", devicePath, config);
        ModifyCloudToDeviceConfigRequest req = new ModifyCloudToDeviceConfigRequest();

        try {
            // Data sent through the wire has to be base64 encoded.
            String encPayload = Base64.getEncoder().encodeToString(config.getBytes(StandardCharsets.UTF_8.name()));
            req.setBinaryData(encPayload);
        } catch (UnsupportedEncodingException e) {
            logger.error("Config encoding error", e);
            throw new IllegalArgumentException("Config encoding error", e);
        }

        try {
            DeviceConfig deviceConfig = service
                    .projects()
                    .locations()
                    .registries()
                    .devices()
                    .modifyCloudToDeviceConfig(devicePath, req)
                    .execute();
            logger.info("{0} has new config version {1}", devicePath, deviceConfig.getVersion());
        } catch (IOException e) {
            logger.error("Device config update failed", e);
            throw new ConnectorIOException("Device config update failed", e);
        }

    }
}
