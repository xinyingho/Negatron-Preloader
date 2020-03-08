/*
 * This file is part of Negatron.
 * Copyright (C) 2015-2020 BabelSoft S.A.S.U.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.babelsoft.negatron.preloader.spi;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.spi.AbstractResourceBundleProvider;
import net.babelsoft.negatron.preloader.Language;

/**
 *
 * @author capan
 */
public class LanguageUiProviderImpl extends AbstractResourceBundleProvider implements LanguageUiProvider {
    
    @Override
    public ResourceBundle getBundle(String baseName, Locale locale) {
        ResourceBundle bundle = null;

        // Retrieve the resource name
        final String bundleName = toBundleName(Language.Manager.FILE_PATH, locale);
        final String resourceName = ResourceBundle.Control.getControl(
                ResourceBundle.Control.FORMAT_DEFAULT
        ).toResourceName(bundleName, "properties");
        if (resourceName == null) {
            return bundle;
        }
        
        // Retrieve all the potential resource root folders
        final List<String> resourcePaths = new ArrayList<>();
        resourcePaths.add(""); // default path to the current working folder
        String libraryPath = System.getProperty("java.library.path");
        if (libraryPath != null)
            resourcePaths.addAll( Arrays.asList(libraryPath.split(";")) );

        // Search for the resource over all those root folders
        final Optional<Path> optionalPath = resourcePaths.stream().map(
                path -> Paths.get(path, resourceName)
        ).filter(
                path -> Files.exists(path)
        ).findFirst();
        
        // Embed the resource into a bundle
        if (optionalPath.isPresent()) try (
            InputStream stream = Files.newInputStream(optionalPath.get());
            InputStreamReader reader = new InputStreamReader(stream, "UTF-8");
        ) {
            bundle = new PropertyResourceBundle(reader);
        } catch (IOException ex) {
             throw new UncheckedIOException(ex);
        }

        return bundle;
    }
}
