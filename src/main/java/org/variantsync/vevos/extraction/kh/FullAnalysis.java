package org.variantsync.vevos.extraction.kh;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.analysis.AnalysisComponent;
import net.ssehub.kernel_haven.analysis.PipelineAnalysis;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.util.null_checks.NonNull;
import net.ssehub.kernel_haven.fe_analysis.pcs.CodeBlockAnalysis;

public class FullAnalysis extends PipelineAnalysis {

    /**
     * Creates a new {@link FullAnalysis}.
     *
     * @param config The global configuration.
     */
    public FullAnalysis(@NonNull Configuration config) {
        super(config);
    }

    @Override
    protected @NonNull AnalysisComponent<?> createPipeline() throws SetUpException {
        return new CodeBlockAnalysis(config, getCmComponent(), getBmComponent(), getVmComponent());
    }

}
