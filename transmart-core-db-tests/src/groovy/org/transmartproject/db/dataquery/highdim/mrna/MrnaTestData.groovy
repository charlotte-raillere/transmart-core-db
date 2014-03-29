package org.transmartproject.db.dataquery.highdim.mrna

import org.transmartproject.db.biomarker.BioMarkerCoreDb
import org.transmartproject.db.dataquery.highdim.DeGplInfo
import org.transmartproject.db.dataquery.highdim.DeSubjectSampleMapping
import org.transmartproject.db.dataquery.highdim.HighDimTestData
import org.transmartproject.db.dataquery.highdim.SampleBioMarkerTestData
import org.transmartproject.db.i2b2data.I2b2Data
import org.transmartproject.db.i2b2data.PatientDimension
import org.transmartproject.db.search.SearchKeywordCoreDb

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.is
import static org.hamcrest.Matchers.notNullValue

class MrnaTestData extends HighDimTestData {

    public static final String TRIAL_NAME = 'MRNA_SAMP_TRIAL'

    SampleBioMarkerTestData bioMarkerTestData = new SampleBioMarkerTestData()

    private String conceptCode

    DeGplInfo platform = {
        def res = new DeGplInfo(
                title: 'Affymetrix Human Genome U133A 2.0 Array',
                organism: 'Homo Sapiens',
                markerTypeId: 'Gene Expression')
        res.id = 'BOGUSGPL570'
        res
    }()

    MrnaTestData(String conceptCode = 'concept code #1') {
        this.conceptCode = conceptCode
    }

    List<BioMarkerCoreDb> getBioMarkers() {
        bioMarkerTestData.geneBioMarkers
    }

    List<SearchKeywordCoreDb> searchKeywords = {
        bioMarkerTestData.geneSearchKeywords +
                bioMarkerTestData.proteinSearchKeywords +
                bioMarkerTestData.geneSignatureSearchKeywords
    }()

    List<DeMrnaAnnotationCoreDb> annotations = {
        def createAnnotation = { probesetId, probeId, BioMarkerCoreDb bioMarker ->
            def res = new DeMrnaAnnotationCoreDb(
                    gplId: platform.id,
                    probeId: probeId,
                    geneSymbol: bioMarker.name,
                    geneId: bioMarker.primaryExternalId,
                    organism: 'Homo sapiens',
            )
            res.id = probesetId
            res
        }
        [
                createAnnotation(-201, '1553506_at', bioMarkers[0]),
                createAnnotation(-202, '1553510_s_at', bioMarkers[1]),
                createAnnotation(-203, '1553513_at', bioMarkers[2]),
        ]
    }()

    List<PatientDimension> patients =
        I2b2Data.createTestPatients(2, -300, TRIAL_NAME)

    List<DeSubjectSampleMapping> assays =
        HighDimTestData.createTestAssays(patients, -400, platform, TRIAL_NAME, conceptCode)

    List<DeSubjectMicroarrayDataCoreDb> microarrayData = {
        def common = [
                trialName: TRIAL_NAME,
                //trialSource: "$TRIAL_NAME:STD" (not mapped)
        ]
        def createMicroarrayEntry = { DeSubjectSampleMapping assay,
                                      DeMrnaAnnotationCoreDb probe,
                                      double intensity ->
            new DeSubjectMicroarrayDataCoreDb(
                    probe: probe,
                    assay: assay,
                    patient: assay.patient,
                    rawIntensity: intensity,
                    logIntensity: Math.log(intensity) / Math.log(2),
                    zscore: intensity * 2, /* non-sensical value */
                    *: common,
            )
        }

        def res = []
        //doubles loose some precision when adding 0.1, so i use BigDecimals instead
        BigDecimal intensity = BigDecimal.ZERO
        annotations.each { probe ->
            assays.each { assay ->
                intensity = intensity + 0.1
                res += createMicroarrayEntry assay, probe, intensity
            }
        }

        res
    }()

    void saveAll() {
        bioMarkerTestData.saveGeneData()

        assertThat platform.save(), is(notNullValue(DeGplInfo))
        save annotations
        save patients
        save assays
        save microarrayData
    }
}
