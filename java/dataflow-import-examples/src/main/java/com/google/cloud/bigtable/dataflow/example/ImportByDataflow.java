/*
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.cloud.bigtable.dataflow.example;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import com.google.bigtable.repackaged.com.google.cloud.grpc.BigtableClusterUtilities;
import com.google.bigtable.repackaged.com.google.com.google.bigtable.admin.v2.Cluster;
import com.google.cloud.bigtable.dataflow.CloudBigtableIO;
import com.google.cloud.bigtable.dataflow.CloudBigtableTableConfiguration;
import com.google.cloud.bigtable.dataflowimport.HBaseImportIO;
import com.google.cloud.bigtable.dataflowimport.HBaseImportOptions;
import com.google.cloud.dataflow.sdk.Pipeline;
import com.google.cloud.dataflow.sdk.io.Read;
import com.google.cloud.dataflow.sdk.options.PipelineOptionsFactory;

/**
 * Example of importing sequence file(s) into Cloud Bigtable using Dataflow.
 */
public class ImportByDataflow {
  private static class Option {
    String name;
    String description;
    String value;
    boolean isFound;

    public Option(String name, String description, String value, boolean isFound) {
      super();
      this.name = name;
      this.description = description;
      this.value = value;
      this.isFound = isFound;
    }
  }

  private static List<Option> options;

  public static void main(String[] args) throws Exception {
    String[] dataflowArgs = processOptions();
    if (!validateOptions()) {
      System.exit(-1);
    }
    printOptions();

    HBaseImportOptions options =
        PipelineOptionsFactory.fromArgs(dataflowArgs).withValidation().as(HBaseImportOptions.class);
    Pipeline p = CloudBigtableIO.initializeForWrite(Pipeline.create(options));
    p
        .apply("ReadSequenceFile", Read.from(HBaseImportIO.createSource(options)))
        .apply("ConvertResultToMutations", HBaseImportIO.transformToMutations())
        .apply("WriteToTable", CloudBigtableIO.writeToTable(
            CloudBigtableTableConfiguration.fromCBTOptions(options)));
    p.run();
  }

  private static void printOptions() {
    for (Option option : options) {
      System.out.println(String.format("For option %s, using value '%s'", option.name, option.value));
    }
  }

  private static String[] processOptions() throws IOException, GeneralSecurityException {
    String dataflowProject = getRequired("dataflow.project", "The Cloud Project for Dataflow.");
    String dataflowZone =
        getOptional("dataflow.zone", null, "The Zone in which to run the Dataflow job");
    String dataflowStaging = getRequired("dataflow.staging",
      "The gcs location that Dataflow should use to deploy jars.");
    String dataflowWorkerMachineType = getOptional("dataflow.worker.machine.type", "n1-standard-4",
      "The worker type to use in Dataflow.  Cloud Bigtable benefits from larger machines.  The default is n1-standard-4");
    String dataflowWorkerCount = getOptional("dataflow.worker.count", "null",
      "The number of dataflow workers. The default is proportional to the number of nodes in your cluster.");

    String bigtableProject = getOptional("bigtable.project", dataflowProject,
      "The Cloud Project for the Dataflow instance.  Defaults to -Ddataflow.project.");
    String bigtableInstance = getRequired("bigtable.instance", "The Cloud Bigtable instance id");
    String bigtableTable =
        getRequired("bigtable.table", "The name of the Cloud Bigtable table to write into");

    String filePattern = getRequired("gcs.file.pattern",
      "The prefix of the files to import in gcs.  For example: gs://your_bucket/import_dir/part-");

    if (bigtableProject != null && bigtableInstance != null) {
      final BigtableClusterUtilities utility = BigtableClusterUtilities.forInstance(bigtableProject, bigtableInstance);
      if (utility.getClusters().getClustersCount() == 0) {
        System.err
            .println(String.format("Could not find a valid instance for project %s, instance: %s",
              bigtableProject, bigtableInstance));
        System.exit(-1);
      }
      Cluster cluster = utility.getClusters().getClusters(0);
      if (dataflowZone == null) {
        dataflowZone = cluster.getLocation();
      }
      if (dataflowWorkerCount == null) {
        // Each Bigtable node can roughly handle 3 dataflow worker CPUs.
        double machineCount = Integer.parseInt(dataflowWorkerMachineType.split("-")[2]);
        dataflowWorkerCount = String.valueOf((int) Math.ceil(cluster.getServeNodes() * 3.0 / machineCount));
      }
    }

    return new String[] { "--runner=BlockingDataflowPipelineRunner",
        "--project=" + dataflowProject,
        "--zone=" + dataflowZone,
        "--stagingLocation=" + dataflowStaging,
        "--workerMachineType=" + dataflowWorkerMachineType,
        "--numWorkers=" + dataflowWorkerCount,

        "--bigtableProjectId=" + bigtableProject,
        "--bigtableInstanceId=" + bigtableInstance,
        "--bigtableTableId=" + bigtableTable,
        "--filePattern=" + filePattern
    };
  }

  private static String getOptional(String key, String defaultValue, String description) {
    final String value = System.getProperty(key, defaultValue);
    options.add(new Option(key, description, value, true));
    return value;
  }

  private static String getRequired(String key, String description) {
    final String value = System.getProperty(key);
    options.add(new Option(key, description, value, value != null));
    return value;
  }


  /**
   * Validates the options, and prints missing options
   * @return true if the optiosn are all filled in
   */
  private static boolean validateOptions() {
    List<Option> missingOptions = new ArrayList<>();
    for (Option option : options) {
      if (!option.isFound) {
        missingOptions.add(option);
      }
    }

    if (!missingOptions.isEmpty()) {
      System.out.println("The following options are missing:");
      for (Option option : missingOptions) {
        System.out.println("\t-D" + option.name);
        System.out.println("\t\tDescription: " + option.description);
      }
    }

    return missingOptions.isEmpty();
  }

}
