module io.requery {
    exports io.requery;
    exports io.requery.async;
    exports io.requery.cache;
    exports io.requery.converter;
    exports io.requery.meta;
    exports io.requery.proxy;
    exports io.requery.query;
    exports io.requery.query.element;
    exports io.requery.query.function;
    exports io.requery.sql;
    exports io.requery.sql.gen;
    exports io.requery.sql.platform;
    exports io.requery.sql.type;
    exports io.requery.util;
    exports io.requery.util.function;
    exports io.requery.reactivex;
    exports io.requery.reactivex3;
    exports io.requery.reactor;
    
    requires transitive java.sql;
    requires static jakarta.transaction;
    requires static javax.cache;
    requires static io.reactivex.rxjava2;
    requires static io.reactivex.rxjava3;
    requires static reactor.core;
    requires static jdk.unsupported;  // Required for Unsafe usage
}
