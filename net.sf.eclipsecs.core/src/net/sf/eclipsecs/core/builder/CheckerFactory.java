//============================================================================
//
// Copyright (C) 2002-2012  David Schneider, Lars K�dderitzsch
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//============================================================================

package net.sf.eclipsecs.core.builder;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.sf.eclipsecs.core.CheckstylePlugin;
import net.sf.eclipsecs.core.config.CheckstyleConfigurationFile;
import net.sf.eclipsecs.core.config.ConfigurationReader;
import net.sf.eclipsecs.core.config.ConfigurationReader.AdditionalConfigData;
import net.sf.eclipsecs.core.config.ICheckConfiguration;
import net.sf.eclipsecs.core.config.configtypes.IContextAware;
import net.sf.eclipsecs.core.util.CheckstylePluginException;

import org.apache.commons.io.IOUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.xml.sax.InputSource;

import com.google.common.collect.MapMaker;
import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.ConfigurationLoader;
import com.puppycrawl.tools.checkstyle.PropertyResolver;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;

/**
 * Factory class to create (and cache) checker objects.
 * 
 * @author Lars K�dderitzsch
 */
public final class CheckerFactory {

    //
    // class attributes
    //

    /** Map containing the configured checkers. */
    private static Map<String, Checker> sCheckerMap;

    /** Map containing the modification times of configs. */
    private static Map<String, Long> sModifiedMap;

    /** Map containing additional data about the check configurations. */
    private static Map<String, AdditionalConfigData> sAdditionalDataMap;

    /** the shared classloader for the checkers. */
    private static ProjectClassLoader sSharedClassLoader;

    //
    // static initializer
    //

    /**
     * Initialize the cache.
     */
    static {

        // Use synchronized collections to avoid concurrent modification
        sCheckerMap = new MapMaker().softValues().makeMap();
        sModifiedMap = Collections.synchronizedMap(new HashMap<String, Long>());
        sAdditionalDataMap = Collections.synchronizedMap(new HashMap<String, AdditionalConfigData>());

        sSharedClassLoader = new ProjectClassLoader();
    }

    //
    // constructors
    //

    /**
     * Hidden utility class constructor.
     */
    private CheckerFactory() {
        // noop
    }

    //
    // methods
    //

    /**
     * Creates a checker for a given configuration file.
     * 
     * @param config
     *            the check configuration data
     * @param project
     *            the project to create the checker for
     * @return the checker for the given configuration file
     * @throws CheckstyleException
     *             the configuration file had errors
     * @throws CheckstylePluginException
     *             the configuration could not be read
     */
    public static Checker createChecker(ICheckConfiguration config, IProject project) throws CheckstyleException,
        CheckstylePluginException {

        String cacheKey = getCacheKey(config, project);

        CheckstyleConfigurationFile configFileData = config.getCheckstyleConfiguration();
        Checker checker = tryCheckerCache(cacheKey, configFileData.getModificationStamp());

        // no cache hit
        if (checker == null) {
            PropertyResolver resolver = configFileData.getPropertyResolver();

            // set the project context if the property resolver needs the
            // context
            if (resolver instanceof IContextAware) {
                ((IContextAware) resolver).setProjectContext(project);
            }

            InputSource in = null;
            try {
                in = configFileData.getCheckConfigFileInputSource();
                checker = createCheckerInternal(in, resolver, project);
            }
            finally {
                IOUtils.closeQuietly(in.getByteStream());
            }

            // store checker in cache
            Long modified = new Long(configFileData.getModificationStamp());
            sCheckerMap.put(cacheKey, checker);
            sModifiedMap.put(cacheKey, modified);
        }

        return checker;
    }

    /**
     * Determines the additional data for a given configuration file.
     * 
     * @param config
     *            the check configuration
     * @param project
     *            the project to create the checker for
     * @return the checker for the given configuration file
     * @throws CheckstylePluginException
     *             the configuration could not be read
     */
    public static ConfigurationReader.AdditionalConfigData getAdditionalData(ICheckConfiguration config,
        IProject project) throws CheckstylePluginException {

        String cacheKey = getCacheKey(config, project);

        AdditionalConfigData additionalData = sAdditionalDataMap.get(cacheKey);

        // no cache hit - create the additional data
        if (additionalData == null) {
            CheckstyleConfigurationFile configFileData = config.getCheckstyleConfiguration();

            InputSource in = null;
            try {
                in = configFileData.getCheckConfigFileInputSource();
                additionalData = ConfigurationReader.getAdditionalConfigData(in);
            }
            finally {
                IOUtils.closeQuietly(in.getByteStream());
            }

            sAdditionalDataMap.put(cacheKey, additionalData);
        }

        return additionalData;
    }

    /**
     * Returns the shared classloader which is used by all checkers created by this factory.
     * 
     * @return the shared classloader
     */
    public static ProjectClassLoader getSharedClassLoader() {
        return sSharedClassLoader;
    }

    /**
     * Cleans up the checker cache.
     */
    public static void cleanup() {
        sCheckerMap.clear();
        sModifiedMap.clear();
        sAdditionalDataMap.clear();
    }

    /**
     * Build a unique cache key for the check configuration.
     * 
     * @param config
     *            the check configuration
     * @param project
     *            the project being checked
     * @return the unique cache key
     * @throws CheckstylePluginException
     *             error getting configuration file data
     */
    private static String getCacheKey(ICheckConfiguration config, IProject project) throws CheckstylePluginException {
        CheckstyleConfigurationFile configFileData = config.getCheckstyleConfiguration();

        URL configLocation = configFileData.getResolvedConfigFileURL();
        String checkConfigName = config.getName() + "#" + (config.isGlobal() ? "Global" : "Local");

        String cacheKey = project.getName() + "#" + configLocation + "#" + checkConfigName; //$NON-NLS-1$

        return cacheKey;
    }

    /**
     * Tries to reuse an already configured checker for this configuration.
     * 
     * @param config
     *            the configuration file
     * @param cacheKey
     *            the key for cache access
     * @return the cached checker or null
     */
    private static Checker tryCheckerCache(String cacheKey, long modificationStamp) {

        // try the cache
        Checker checker = sCheckerMap.get(cacheKey);

        // if cache hit
        if (checker != null) {

            // compare modification times of the configs
            Long oldTime = sModifiedMap.get(cacheKey);
            Long newTime = new Long(modificationStamp);

            // no match - remove checker from cache
            if (oldTime == null || oldTime.compareTo(newTime) != 0) {
                checker = null;
                sCheckerMap.remove(cacheKey);
                sModifiedMap.remove(cacheKey);
                sAdditionalDataMap.remove(cacheKey);
            }
        }
        return checker;
    }

    /**
     * Creates a new checker and configures it with the given configuration file.
     * 
     * @param input
     *            the input source for the configuration file
     * @param configFileUri
     *            the URI of the configuration file, or <code>null</code> if it could not be determined
     * @param propResolver
     *            a property resolver null
     * @param project
     *            the project
     * @return the newly created Checker
     * @throws CheckstyleException
     *             an exception during the creation of the checker occured
     */
    private static Checker createCheckerInternal(InputSource input, PropertyResolver propResolver, IProject project)
        throws CheckstyleException, CheckstylePluginException {

        // load configuration
        Configuration configuration = ConfigurationLoader.loadConfiguration(input, propResolver, true);

        // create and configure checker
        Checker checker = new Checker();
        checker.setModuleClassLoader(CheckstylePlugin.getDefault().getAddonExtensionClassLoader());
        try {
            checker.setCharset(project.getDefaultCharset());
        }
        catch (UnsupportedEncodingException e) {
            CheckstylePluginException.rethrow(e);
        }
        catch (CoreException e) {
            CheckstylePluginException.rethrow(e);
        }

        // set the eclipse platform locale
        Locale platformLocale = CheckstylePlugin.getPlatformLocale();
        checker.setLocaleLanguage(platformLocale.getLanguage());
        checker.setLocaleCountry(platformLocale.getCountry());
        checker.setClassloader(sSharedClassLoader);

        checker.configure(configuration);

        // reset the basedir if it is set so it won't get into the plugins way
        // of determining workspace resources from checkstyle reported file
        // names, see
        // https://sourceforge.net/tracker/?func=detail&aid=2880044&group_id=80344&atid=559497
        if (checker.getBasedir() != null) {

            // set basedir to null in a hackish way. This is because the regular
            // setter won't accept null
            try {
                Field basedirField = Checker.class.getDeclaredField("mBasedir");
                basedirField.setAccessible(true);
                basedirField.set(checker, null);
            }
            catch (SecurityException e) {
                CheckstylePluginException.rethrow(e);
            }
            catch (NoSuchFieldException e) {
                CheckstylePluginException.rethrow(e);
            }
            catch (IllegalArgumentException e) {
                CheckstylePluginException.rethrow(e);
            }
            catch (IllegalAccessException e) {
                CheckstylePluginException.rethrow(e);
            }
        }

        return checker;
    }
}
