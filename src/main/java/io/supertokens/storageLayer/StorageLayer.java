/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This software is licensed under the Apache License, Version 2.0 (the
 *    "License") as published by the Apache Software Foundation.
 *
 *    You may not use this file except in compliance with the License. You may
 *    obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package io.supertokens.storageLayer;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.cliOptions.CLIOptions;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.inmemorydb.Start;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.Storage;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Iterator;
import java.util.ServiceLoader;

public class StorageLayer extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.storageLayer.StorageLayer";
    private final Storage storageLayer;

    private StorageLayer(Main main, String pluginFolderPath, String configFilePath) throws MalformedURLException {
        Logging.info(main, "Loading storage layer.");
        File loc = new File(pluginFolderPath);

        File[] flist = loc.listFiles(file -> file.getPath().toLowerCase().endsWith(".jar"));

        if (flist == null) {
            throw new QuitProgramException("No database plugin found. Please redownload and install SuperTokens");
        }
        URL[] urls = new URL[flist.length];
        for (int i = 0; i < flist.length; i++) {
            urls[i] = flist[i].toURI().toURL();
        }
        URLClassLoader ucl = new URLClassLoader(urls);

        ServiceLoader<Storage> sl = ServiceLoader.load(Storage.class, ucl);
        Iterator<Storage> it = sl.iterator();
        Storage storageLayerTemp = null;
        while (it.hasNext()) {
            Storage plugin = it.next();
            if (storageLayerTemp == null) {
                storageLayerTemp = plugin;
            } else {
                throw new QuitProgramException(
                        "Multiple database plugins found. Please make sure that just one plugin is in the /plugin " +
                                "folder of the installation. Alternatively, please redownload and install SuperTokens" +
                                ".");
            }
        }
        if (storageLayerTemp == null) {
            throw new QuitProgramException("No database plugin found. Please redownload and install SuperTokens");
        }

        if (!main.isForceInMemoryDB() && (
                storageLayerTemp.canBeUsed(configFilePath) ||
                        CLIOptions.get(main).isForceNoInMemoryDB()
        )) {
            this.storageLayer = storageLayerTemp;
        } else {
            Logging.info(main, "Using in memory storage.");
            this.storageLayer = new Start();
        }
        this.storageLayer.constructor(main.getProcessId(), Main.makeConsolePrintSilent);
        this.storageLayer.loadConfig(configFilePath);
    }

    private static StorageLayer getInstance(Main main) {
        return (StorageLayer) main.getResourceDistributor().getResource(RESOURCE_KEY);
    }

    public static void init(Main main, String pluginFolderPath, String configFilePath) throws MalformedURLException {
        if (getInstance(main) != null) {
            return;
        }
        main.getResourceDistributor()
                .setResource(RESOURCE_KEY, new StorageLayer(main, pluginFolderPath, configFilePath));
    }

    public static Storage getStorageLayer(Main main) {
        if (getInstance(main) == null) {
            throw new QuitProgramException("please call init() before calling getStorageLayer");
        }
        return getInstance(main).storageLayer;
    }
}
