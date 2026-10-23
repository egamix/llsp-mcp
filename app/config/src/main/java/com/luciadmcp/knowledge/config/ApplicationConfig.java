package com.luciadmcp.knowledge.config;

import java.nio.file.*;
import java.io.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** The sole serve-time operator configuration. Unknown keys fail closed. */
public record ApplicationConfig(Path indexRoot,String sourceDefault,int port,boolean stdio) {
    public ApplicationConfig {
        Objects.requireNonNull(indexRoot);
        if(sourceDefault==null || !sourceDefault.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,127}") || port<1 || port>65535)
            throw new IllegalArgumentException("INVALID_CONFIGURATION");
        indexRoot=indexRoot.toAbsolutePath().normalize();
    }
    public static ApplicationConfig load(Path path) {
        var options=new LoaderOptions();options.setAllowDuplicateKeys(false);options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(8);options.setCodePointLimit(65536);
        try(var input=Files.newInputStream(path)) {
            byte[] bytes=input.readNBytes(65537);if(bytes.length>65536)throw new IllegalArgumentException();
            Object value=new Yaml(new SafeConstructor(options)).load(new ByteArrayInputStream(bytes));
            if(!(value instanceof Map<?,?> m) || !Set.of("indexRoot","sourceDefault","port","stdio").containsAll(m.keySet())
                || !(m.get("indexRoot") instanceof String root) || !(m.get("sourceDefault") instanceof String source))throw new IllegalArgumentException();
            Object p=m.containsKey("port")?m.get("port"):8766;
            Object s=m.containsKey("stdio")?m.get("stdio"):false;
            if(!(p instanceof Integer port) || !(s instanceof Boolean stdio))throw new IllegalArgumentException();
            Path resolved=Path.of(root);if(!resolved.isAbsolute())resolved=path.toAbsolutePath().getParent().resolve(resolved);
            return new ApplicationConfig(resolved,source,port,stdio);
        } catch(Exception e) { throw new IllegalArgumentException("INVALID_CONFIGURATION: application.yaml is invalid or unavailable"); }
    }
}
