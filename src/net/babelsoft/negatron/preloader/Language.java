/*
 * This file is part of Negatron.
 * Copyright (C) 2015-2018 BabelSoft S.A.S.U.
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

import java.util.ResourceBundle;

/**
 *
 * @author capan
 */
public enum Language {
    Manager;
    
    public final String ROOT_PATH = "theme/language";
    public final String MASK = "preload.*\\.properties";
    public final String FILE_PATH = ROOT_PATH + "/preload";
    private final String RESOURCE_BUNDLE_SPI = "net.babelsoft.negatron.preloader.LanguageUi";
    
    public ResourceBundle getBundle() {
        return ResourceBundle.getBundle(RESOURCE_BUNDLE_SPI);
    }
    
    public String getString(String key) {
        return getBundle().getString(key);
    }
}