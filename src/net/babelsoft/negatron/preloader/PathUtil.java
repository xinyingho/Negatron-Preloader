/*
 * This file is part of Negatron.
 * Copyright (C) 2015-2025 BabelSoft S.A.S.U.
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
package net.babelsoft.negatron.preloader;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 *
 * @author capan
 */
public final class PathUtil {
    
    private PathUtil() { }
    
    public enum PathType {
        FILE,
        FOLDER
    }
    
    public static Path retrieveFromJavaLibraryPaths(PathType pathType, String... pathComponents) {
        // Retrieve all the potential root folders
        final List<String> rootFolders = new ArrayList<>();
        rootFolders.add(""); // default path to the current working folder
        String exePath = System.getProperty("jpackage.app-path");
        if (exePath != null && !exePath.isBlank()) {
            // required for the Linux packaged versions
            Path appPath = Paths.get(exePath).getParent().resolveSibling("lib/app");
            rootFolders.add(appPath.toString());
        }
        String libraryPath = System.getProperty("java.library.path");
        if (libraryPath != null)
            rootFolders.addAll( Arrays.asList(libraryPath.split(File.pathSeparator)) );

        // Search for the first valid path over all those root folders
        final Optional<Path> opath = rootFolders.stream().map(
            path -> Paths.get(path, pathComponents)
        ).filter(
            path -> Files.exists(path) && (
                pathType == PathType.FILE && Files.isRegularFile(path) ||
                pathType == PathType.FOLDER && Files.isDirectory(path)
            )
        ).findFirst();
        
        return opath.orElse(null);
    }
}
