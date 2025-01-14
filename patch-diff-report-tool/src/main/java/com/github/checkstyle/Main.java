////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2016 the original author or authors.
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
////////////////////////////////////////////////////////////////////////////////

package com.github.checkstyle;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.github.checkstyle.data.CliPaths;
import com.github.checkstyle.data.DiffReport;
import com.github.checkstyle.data.MergedConfigurationModule;
import com.github.checkstyle.parser.StaxConfigurationParser;
import com.github.checkstyle.parser.StaxContentParser;
import com.github.checkstyle.site.JxrDummyLog;
import com.github.checkstyle.site.SiteGenerator;

/**
 * Utility class, contains main function and its auxiliary routines.
 *
 * @author attatrol
 */
public final class Main {

    /**
     * Help message.
     */
    public static final String MSG_HELP = "This program creates symmetric difference "
            + "from two checkstyle-result.xml reports\n"
            + "generated for checkstyle build.\n"
            + "Command line arguments:\n"
            + "\t--baseReportPath - path to the directory containing base checkstyle-result.xml, "
            + "obligatory argument;\n"
            + "\t--patchReportPath - path to the directory containing patch checkstyle-result.xml, "
            + "also obligatory argument;\n"
            + "\t--sourcePath - path to the data under check (optional, if absent then file "
            + "structure for cross reference files won't be relativized, "
            + "full paths will be used);\n"
            + "\t--resultPath - path to the resulting site (optional, if absent then default "
            + "path will be used: ~/XMLDiffGen_report_yyyy.mm.dd_hh_mm_ss), remember, "
            + "if this folder exists its content will be purged;\n"
            + "\t-h - simply shows help message.";

    /**
     * Number of "file" xml tags parsed at one iteration of parser.
     */
    public static final int XML_PARSE_PORTION_SIZE = 50;

    /**
     * Name for the site file.
     */
    public static final Path CONFIGPATH = Paths.get("configuration.html");

    /**
     * Name for command line option "baseReportPath".
     */
    private static final String OPTION_BASE_REPORT_PATH = "baseReport";

    /**
     * Name for command line option "patchReportPath".
     */
    private static final String OPTION_PATCH_REPORT_PATH = "patchReport";

    /**
     * Name for command line option "sourcePath".
     */
    private static final String OPTION_SOURCE_PATH = "refFiles";

    /**
     * Name for command line option "resultPath".
     */
    private static final String OPTION_RESULT_FOLDER_PATH = "output";

    /**
     * Name for command line option "baseConfigPath".
     */
    private static final String OPTION_BASE_CONFIG_PATH = "baseConfig";

    /**
     * Name for command line option "patchConfigPath".
     */
    private static final String OPTION_PATCH_CONFIG_PATH = "patchConfig";

    /**
     * Name for command line option that shows help message.
     */
    private static final String OPTION_HELP = "h";

    /**
     * Private ctor, see main method.
     */
    private Main() {

    }

    /**
     * Executes all three processing stages according to CLI options.
     *
     * @param args
     *        cli arguments.
     * @throws Exception
     *         on failure to execute stages.
     */
    public static void main(final String... args) throws Exception {
        System.out.println("patch-diff-report-tool execution started.");
        final CommandLine commandLine = parseCli(args);
        if (commandLine.hasOption(OPTION_HELP)) {
            System.out.println(MSG_HELP);
        }
        else {
            final CliPaths paths = parseCliToPojo(commandLine);

            //preparation stage, checks validity of input paths
            System.out.println("Preparation stage is started.");
            PreparationUtils.checkFilesExistence(paths);
            PreparationUtils.exportResources(paths);

            //XML parsing stage
            System.out.println("XML parsing is started.");
            final DiffReport diffReport =
                    StaxContentParser.parse(paths.getBaseReportPath(),
                    paths.getPatchReportPath(), XML_PARSE_PORTION_SIZE);

            //Configuration processing stage.
            final MergedConfigurationModule configuration;
            if (paths.configurationPresent()) {
                System.out.println("Creation of configuration report is started.");
                configuration = StaxConfigurationParser.parse(paths.getBaseConfigPath(),
                                paths.getPatchConfigPath());
            }
            else {
                System.out.println("Cronfiguration processing skipped: "
                        + "no configuration paths provided.");
                configuration = null;
            }

            //Site and XREF generation stage
            System.out.println("Creation of diff html site is started.");
            try {
                SiteGenerator.generate(diffReport, paths, configuration);
            }
            finally {
                for (String message : JxrDummyLog.getLogs()) {
                    System.out.println(message);
                }
            }
            System.out.println("Creation of the result site succeed.");
        }
        System.out.println("patch-diff-report-tool execution ended.");
    }

    /**
     * Parses CLI.
     *
     * @param args
     *        command line parameters
     * @return parsed information about passed parameters
     * @throws ParseException
     *         when passed arguments are not valid
     */
    private static CommandLine parseCli(String... args)
            throws ParseException {
        // parse the parameters
        final CommandLineParser clp = new DefaultParser();
        // always returns not null value
        return clp.parse(buildOptions(), args);
    }

    /**
     * Forms POJO containing input paths.
     *
     * @param commandLine
     *        parsed CLI.
     * @return POJO instance.
     * @throws IllegalArgumentException
     *         on failure to find necessary arguments.
     */
    private static CliPaths parseCliToPojo(CommandLine commandLine)
            throws IllegalArgumentException {
        final Path xmlBasePath = getPath(OPTION_BASE_REPORT_PATH, commandLine, null);
        final Path xmlPatchPath = getPath(OPTION_PATCH_REPORT_PATH, commandLine, null);
        final Path sourcePath = getPath(OPTION_SOURCE_PATH, commandLine, null);
        final Path defaultResultPath = Paths.get(System.getProperty("user.home"))
                .resolve("XMLDiffGen_report_" + new SimpleDateFormat("yyyy.MM.dd_HH_mm_ss")
                        .format(Calendar.getInstance().getTime()));
        final Path resultPath =
                getPath(OPTION_RESULT_FOLDER_PATH, commandLine, defaultResultPath);
        final Path configBasePath = getPath(OPTION_BASE_CONFIG_PATH, commandLine, null);
        final Path configPatchPath =
                getPath(OPTION_PATCH_CONFIG_PATH, commandLine, null);
        return new CliPaths(xmlBasePath, xmlPatchPath, sourcePath,
                resultPath, configBasePath, configPatchPath);
    }

    /**
     * Builds and returns list of parameters supported by this utility.
     *
     * @return available options.
     */
    private static Options buildOptions() {
        final Options options = new Options();
        options.addOption(null, OPTION_BASE_REPORT_PATH, true,
                "Path to the directory containing base checkstyle-report.xml");
        options.addOption(null, OPTION_PATCH_REPORT_PATH, true,
                "Path to the directory containing patch checkstyle-report.xml");
        options.addOption(null, OPTION_SOURCE_PATH, true,
                "Path to the directory containing source under checkstyle check, optional.");
        options.addOption(null, OPTION_RESULT_FOLDER_PATH, true,
                "Path to directory where result path will be stored.");
        options.addOption(null, OPTION_BASE_CONFIG_PATH, true,
                "Path to the configuration of the base report.");
        options.addOption(null, OPTION_PATCH_CONFIG_PATH, true,
                "Path to the configuration of the patch report.");
        options.addOption(OPTION_HELP, false,
                "Shows help message, nothing else.");
        return options;
    }

    /**
     * Generates path from CLI option.
     *
     * @param optionName
     *        name of the option.
     * @param commandLine
     *        parsed CLI.
     * @param alternativePath
     *        path which is used if CLI option is absent.
     * @return generated path.
     */
    private static Path getPath(String optionName,
            CommandLine commandLine, Path alternativePath) {
        final Path path;
        if (commandLine.hasOption(optionName)) {
            path = Paths.get(commandLine.getOptionValue(optionName));
        }
        else {
            path = alternativePath;
        }
        return path;
    }

}
