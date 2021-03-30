package de.hub.mse.variantsync.datasets.kh;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.analysis.AnalysisComponent;
import net.ssehub.kernel_haven.analysis.PipelineAnalysis;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.util.null_checks.NonNull;
import net.ssehub.kernel_haven.fe_analysis.pcs.CodeBlockAnalysis;

public class VariabilityAnalysis extends PipelineAnalysis {

    /**
     * Creates a new {@link VariabilityAnalysis}.
     *
     * @param config The global configuration.
     */
    public VariabilityAnalysis(@NonNull Configuration config) {
        super(config);
    }

    @Override
    protected @NonNull AnalysisComponent<?> createPipeline() throws SetUpException {
        // TODO: Implement feature model extraction
        // TODO: Reintegrate BM extractor into properties file
        // TODO: Change name of csv file to a more generic one
        return new CodeBlockAnalysis(config, getCmComponent(), getBmComponent());
    }

}
