package org.opencb.opencga.analysis.files;

import junit.framework.TestCase;
import org.junit.Test;
import org.opencb.opencga.catalog.models.File;

import java.net.URI;

import static org.junit.Assert.assertEquals;

public class BioformatDetectorTest {

    @Test
    public void detectVariant() {
        assertEquals(File.Bioformat.VARIANT, BioformatDetector.detect(URI.create("file:///test.vcf")));
        assertEquals(File.Bioformat.VARIANT, BioformatDetector.detect(URI.create("file:///test.vcf.variants.json")));
        assertEquals(File.Bioformat.VARIANT, BioformatDetector.detect(URI.create("file:///test.vcf.variants.json.gz")));
        assertEquals(File.Bioformat.VARIANT, BioformatDetector.detect(URI.create("file:///test.vcf.gz")));
        assertEquals(File.Bioformat.VARIANT, BioformatDetector.detect(URI.create("file:///test.vcf.gz.variants.json")));
        assertEquals(File.Bioformat.VARIANT, BioformatDetector.detect(URI.create("file:///test.vcf.gz.variants.json.gz")));

        assertEquals(File.Bioformat.VARIANT, BioformatDetector.detect(URI.create("file:///test.bcf")));
        assertEquals(File.Bioformat.VARIANT, BioformatDetector.detect(URI.create("file:///test.bcf.variants.json")));
        assertEquals(File.Bioformat.VARIANT, BioformatDetector.detect(URI.create("file:///test.bcf.variants.json.gz")));
        assertEquals(File.Bioformat.VARIANT, BioformatDetector.detect(URI.create("file:///test.bcf.gz")));
        assertEquals(File.Bioformat.VARIANT, BioformatDetector.detect(URI.create("file:///test.bcf.gz.variants.json")));
        assertEquals(File.Bioformat.VARIANT, BioformatDetector.detect(URI.create("file:///test.bcf.gz.variants.json.gz")));
        assertEquals(File.Bioformat.VARIANT, BioformatDetector.detect(URI.create("file:///test.bcf.gz.variants.json.snappy")));
        assertEquals(File.Bioformat.VARIANT, BioformatDetector.detect(URI.create("file:///test.bcf.gz.variants.json.snz")));

        assertEquals(File.Bioformat.NONE, BioformatDetector.detect(URI.create("file:///test.vcf.tbi")));
        assertEquals(File.Bioformat.NONE, BioformatDetector.detect(URI.create("file:///test.vcf.txt")));
        assertEquals(File.Bioformat.NONE, BioformatDetector.detect(URI.create("file:///test.vcf.json")));
    }

    @Test
    public void detectAlignment() {
        assertEquals(File.Bioformat.ALIGNMENT, BioformatDetector.detect(URI.create("file:///test.bam")));
        assertEquals(File.Bioformat.ALIGNMENT, BioformatDetector.detect(URI.create("file:///test.sam")));
        assertEquals(File.Bioformat.ALIGNMENT, BioformatDetector.detect(URI.create("file:///test.cram")));
        assertEquals(File.Bioformat.ALIGNMENT, BioformatDetector.detect(URI.create("file:///test.sam.gz")));
        assertEquals(File.Bioformat.ALIGNMENT, BioformatDetector.detect(URI.create("file:///test.bam.alignments.json")));
        assertEquals(File.Bioformat.ALIGNMENT, BioformatDetector.detect(URI.create("file:///test.bam.alignments.json.gz")));
        assertEquals(File.Bioformat.ALIGNMENT, BioformatDetector.detect(URI.create("file:///test.bam.alignments.json.snz")));
        assertEquals(File.Bioformat.ALIGNMENT, BioformatDetector.detect(URI.create("file:///test.bam.alignments.avro")));
        assertEquals(File.Bioformat.ALIGNMENT, BioformatDetector.detect(URI.create("file:///test.bam.alignments.avro.gz")));
        assertEquals(File.Bioformat.ALIGNMENT, BioformatDetector.detect(URI.create("file:///test.bam.alignments.avro.snz")));
    }

    @Test
    public void detectPedigree() {
        assertEquals(File.Bioformat.PEDIGREE, BioformatDetector.detect(URI.create("file:///test.ped")));
        assertEquals(File.Bioformat.PEDIGREE, BioformatDetector.detect(URI.create("file:///test.ped.gz")));
    }

    @Test
    public void detectSequence() {
        assertEquals(File.Bioformat.SEQUENCE, BioformatDetector.detect(URI.create("file:///test.fastq")));
        assertEquals(File.Bioformat.SEQUENCE, BioformatDetector.detect(URI.create("file:///test.fastq.gz")));
    }

}