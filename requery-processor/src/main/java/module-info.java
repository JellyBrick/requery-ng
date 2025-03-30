module io.requery.processor {
    exports io.requery.processor;
    
    requires transitive io.requery;
    requires jakarta.persistence;
    requires jakarta.annotation;
    requires com.squareup.javapoet;
    
    provides javax.annotation.processing.Processor with io.requery.processor.EntityProcessor;
}
