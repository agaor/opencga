package org.opencb.opencga.storage.core.variant.annotation;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.babelomics.biodb.lib.io.BioDBQueryManager;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.avro.VariantAnnotation;
import org.opencb.datastore.core.ObjectMap;
import org.opencb.datastore.core.QueryOptions;
import org.opencb.opencga.storage.core.config.StorageConfiguration;

import java.io.IOException;
import java.util.List;

/**
 * Created by mmedina on 23/11/16.
 */
public class BioDBVariantAnnotator extends VariantAnnotator{

//    private final JsonFactory factory;
    private final QueryOptions queryOptions = new QueryOptions("post", true).append("exclude", "expression");
    private BioDBQueryManager qm = new BioDBQueryManager("http://test.babelomics.org/biodb/rest", "BioDB");
    private ObjectMapper jsonObjectMapper;

    public BioDBVariantAnnotator(StorageConfiguration storageConfiguration, ObjectMap options) throws VariantAnnotatorException {
        this(storageConfiguration, options, true);
    }
    public BioDBVariantAnnotator(StorageConfiguration storageConfiguration, ObjectMap options, boolean restConnection) throws VariantAnnotatorException {
        super(storageConfiguration, options);
    }

    @Override
    public List<VariantAnnotation> annotate(List<Variant> variants) throws IOException {
        return null;
    }
}
