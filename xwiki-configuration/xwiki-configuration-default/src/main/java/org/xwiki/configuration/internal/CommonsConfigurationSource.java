/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.configuration.internal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;

import org.apache.commons.configuration.Configuration;
import org.xwiki.component.annotation.Requirement;
import org.xwiki.component.logging.AbstractLogEnabled;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.properties.ConverterManager;

/**
 * Wrap a Commons Configuration instance into a XWiki {@link ConfigurationSource}. This allows us to reuse the <a href=
 * "http://commons.apache.org/configuration/"numerous types of Configuration<a/> provided by Commons Configuration
 * (properties file, XML files, databases, etc).
 * 
 * @version $Id$
 * @since 1.6M1
 */
public class CommonsConfigurationSource extends AbstractLogEnabled implements ConfigurationSource
{
    private Configuration configuration;

    /**
     * Component used for performing type conversions.
     */
    @Requirement
    private ConverterManager converterManager;

    protected void setConfiguration(Configuration configuration)
    {
        this.configuration = configuration;
    }

    /**
     * {@inheritDoc}
     * 
     * @see ConfigurationSource#getProperty(String, Object)
     */
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, T defaultValue)
    {
        return getProperty(key, defaultValue, (Class<T>) defaultValue.getClass());
    }

    /**
     * {@inheritDoc}
     * 
     * @see ConfigurationSource#getProperty(String)
     */
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key)
    {
        return (T) this.configuration.getProperty(key);
    }

    /**
     * {@inheritDoc}
     * 
     * @see ConfigurationSource#getProperty(String, Class)
     */
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, Class<T> valueClass)
    {
        T result;
        try {
            if (String.class.getName().equals(valueClass.getName())) {
                result = (T) this.configuration.getString(key);
            } else if (List.class.getName().equals(valueClass.getName())) {
                result = (T) this.configuration.getList(key);
            } else if (Properties.class.getName().equals(valueClass.getName())) {
                result = (T) this.configuration.getProperties(key);
            } else if (null != getProperty(key)) {
                result = this.converterManager.convert(valueClass, getProperty(key));
            } else {
                throw new NoSuchElementException("No property named [" + key + "] is set");
            }
        } catch (org.apache.commons.configuration.ConversionException e) {
            throw new org.xwiki.configuration.ConversionException("Key [" + key + "] is not of type ["
                + valueClass.getName() + "]", e);
        } catch (org.xwiki.properties.converter.ConversionException e) {
            throw new org.xwiki.configuration.ConversionException("Key [" + key + "] is not of type ["
                + valueClass.getName() + "]", e);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     * 
     * @see ConfigurationSource#getKeys()
     */
    @SuppressWarnings("unchecked")
    public List<String> getKeys()
    {
        List<String> keysList = new ArrayList<String>();
        Iterator keys = this.configuration.getKeys();
        while (keys.hasNext()) {
            keysList.add((String) keys.next());
        }
        return keysList;
    }

    /**
     * {@inheritDoc}
     * 
     * @see ConfigurationSource#containsKey(String)
     */
    public boolean containsKey(String key)
    {
        return this.configuration.containsKey(key);
    }

    /**
     * {@inheritDoc}
     * 
     * @see ConfigurationSource#isEmpty()
     */
    public boolean isEmpty()
    {
        return this.configuration.isEmpty();
    }

    private <T> T getProperty(String key, T defaultValue, Class<T> valueClass)
    {
        T result = getProperty(key, valueClass);
        if (result == null) {
            result = defaultValue;
        }
        return result;
    }

}
