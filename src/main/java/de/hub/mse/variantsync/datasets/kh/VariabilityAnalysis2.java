package de.hub.mse.variantsync.datasets.kh;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.analysis.AbstractAnalysis;
import net.ssehub.kernel_haven.analysis.AnalysisComponent;
import net.ssehub.kernel_haven.analysis.PipelineAnalysis;
import net.ssehub.kernel_haven.build_model.BuildModel;
import net.ssehub.kernel_haven.code_model.SourceFile;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.fe_analysis.pcs.CodeBlockAnalysis;
import net.ssehub.kernel_haven.util.ExtractorException;
import net.ssehub.kernel_haven.util.null_checks.NonNull;
import net.ssehub.kernel_haven.variability_model.VariabilityModel;

public class VariabilityAnalysis2 extends AbstractAnalysis {

    /**
     * Creates a new {@link VariabilityAnalysis2}.
     *
     * @param config The global configuration.
     */
    public VariabilityAnalysis2(@NonNull Configuration config) {
        super(config);
    }


    @Override
    public void run() {
        try {
            cmProvider.start();

            // code
            int numCm = 0;
            SourceFile<?> result;
            do {
                result = cmProvider.getNextResult();
                if (result != null) {
                    numCm++;
                }
            } while (result != null);
            LOGGER.logInfo("Got " + numCm + " source files in the code model");

            ExtractorException cmExc;
            do {
                cmExc = cmProvider.getNextException();
                if (cmExc != null) {
                    LOGGER.logExceptionInfo("Got an exception from the code model extractor", cmExc);
                }
            } while (cmExc != null);

        } catch (SetUpException e) {
            LOGGER.logException("Exception while starting extractors", e);
        }
    }
}
