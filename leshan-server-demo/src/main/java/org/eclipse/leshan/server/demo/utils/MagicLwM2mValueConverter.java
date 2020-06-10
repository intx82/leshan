/*******************************************************************************
 * Copyright (c) 2015 Sierra Wireless and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *     Sierra Wireless                               - initial API and implementation
 *     Kai Hudalla (Bosch Software Innovations GmbH) - remove dependency on
 *                                                     javax.xml.bind.DatatypeConverter
 *                                                     which is not available on Android
 *******************************************************************************/
package org.eclipse.leshan.server.demo.utils;

import java.util.Base64;
import java.util.Date;
import java.util.regex.Pattern;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.eclipse.leshan.core.model.ResourceModel.Type;
import org.eclipse.leshan.core.node.LwM2mPath;
import org.eclipse.leshan.core.node.codec.CodecException;
import org.eclipse.leshan.core.node.codec.LwM2mValueConverter;
import org.eclipse.leshan.util.Hex;
import org.eclipse.leshan.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link LwM2mValueConverter} which will do some magic conversion.
 */
public class MagicLwM2mValueConverter implements LwM2mValueConverter {

    private static final Logger LOG = LoggerFactory.getLogger(MagicLwM2mValueConverter.class);

    @Override
    public Object convertValue(Object value, Type currentType, Type expectedType, LwM2mPath resourcePath)
            throws CodecException {
        if (expectedType == null) {
            // unknown resource, trusted value
            return value;
        }

        if (currentType == expectedType) {
            // expected type
            return value;
        }

        // We received a value with an unexpected type.
        // Let's do some magic to try to convert this value...

        switch (expectedType) {
        case INTEGER:
            switch (currentType) {
            case FLOAT:
                LOG.debug("Trying to convert float value {} to integer", value);
                Long longValue = ((Double) value).longValue();
                if ((double) value == longValue.doubleValue()) {
                    return longValue;
                }
            default:
                break;
            }
            break;
        case FLOAT:
            switch (currentType) {
            case INTEGER:
                LOG.debug("Trying to convert integer value {} to float", value);
                Double floatValue = ((Long) value).doubleValue();
                if ((long) value == floatValue.longValue()) {
                    return floatValue;
                }
            default:
                break;
            }
            break;
        case BOOLEAN:
            switch (currentType) {
            case STRING:
                LOG.debug("Trying to convert string value {} to boolean", value);
                if (StringUtils.equalsIgnoreCase((String) value, "true")) {
                    return true;
                } else if (StringUtils.equalsIgnoreCase((String) value, "false")) {
                    return false;
                }
                break;
            case INTEGER:
                LOG.debug("Trying to convert int value {} to boolean", value);
                Long val = (Long) value;
                if (val == 1) {
                    return true;
                } else if (val == 0) {
                    return false;
                }
                break;
            default:
                break;
            }
            break;
        case TIME:
            switch (currentType) {
            case INTEGER:
                LOG.debug("Trying to convert long value {} to date", value);
                // let's assume we received the millisecond since 1970/1/1
                return new Date((Long) value);
            case STRING:
                LOG.debug("Trying to convert string value {} to date", value);
                // let's assume we received an ISO 8601 format date
                try {
                    DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();
                    XMLGregorianCalendar cal = datatypeFactory.newXMLGregorianCalendar((String) value);
                    return cal.toGregorianCalendar().getTime();
                } catch (DatatypeConfigurationException | IllegalArgumentException e) {
                    LOG.debug("Unable to convert string to date", e);
                    throw new CodecException("Unable to convert string (%s) to date for resource %s", value,
                            resourcePath);
                }
            default:
                break;
            }
            break;
        case STRING:
            switch (currentType) {
            case BOOLEAN:
            case INTEGER:
            case FLOAT:
                return String.valueOf(value);
            default:
                break;
            }
            break;
        case OPAQUE:
            if (currentType == Type.STRING) {
                String val = (String)value;
                // Check for BASE64
                if (Pattern.compile("[^a-z0-9 ]", Pattern.CASE_INSENSITIVE).matcher(val).find()) {
                    LOG.debug("Trying to convert base64 string to byte array");
                    try {
                        return Base64.getDecoder().decode(val);
                    } catch (Exception e) {
                        throw new CodecException("Unable to convert base64 string to byte array for resource %s", resourcePath);
                    }
                } else {
                // Try with HEX encoding
                    LOG.debug("Trying to convert base64 string to byte array");
                    try {
                        return Hex.decodeHex(val.toCharArray());
                    } catch (IllegalArgumentException e) {
                        throw new CodecException("Unable to convert hexastring to byte array for resource %s", resourcePath);
                    }
                }
            }
            break;
        default:
        }

        throw new CodecException("Invalid value type for resource %s, expected %s, got %s", resourcePath, expectedType,
                currentType);
    }
}
