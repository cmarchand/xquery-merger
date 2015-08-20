package com.oxiane.xquery.merger;

import com.oxiane.xquery.merger.utils.NamespaceMapping;
import com.oxiane.xquery.merger.utils.ParsingException;
import com.oxiane.xquery.merger.utils.XQueryUriResolver;
import com.oxiane.xquery.merger.utils.AppenderFilter;
import com.oxiane.xquery.merger.utils.StringBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

/**
 * Reads a xquery file, and returns the file, with all imported modules included in source code.
 * This class has limitations against XQuery 3.0 syntax, and throws exceptions if xquery file reaches these limitations.
 * 
 * @author ext-cmarchand
 */
public class XQueryMerger {
    private final StreamSource source;
    private final URIResolver uriResolver;
    private Charset charset;
    private NamespaceMapping mapping;
    private static final String CR = System.getProperty("line.separator");
    private List<String> loadedFiles;
    private AppenderFilter filter;
    private boolean mainQuery = false;
    
    public XQueryMerger(StreamSource source) {
        this(source, Charset.defaultCharset());
    }
    
    public XQueryMerger(StreamSource source, Charset charset) {
        this(source, charset, new XQueryUriResolver());
    }
    public XQueryMerger(StreamSource source, URIResolver uriResolver) {
        this(source, Charset.defaultCharset(), uriResolver);
    }
    /**
     * Doit impérativement être appelée lorsqu'on traite le fichier principal
     */
    public void setMainQuery() {
        mainQuery = true;
    }
    
    protected XQueryMerger(StreamSource source, Charset charset, URIResolver uriResolver) {
        super();
        this.source = source;
        this.charset = charset;
        this.uriResolver = uriResolver;
    }
    
    private XQueryMerger setNamespaceMapping(NamespaceMapping mapping) {
        this.mapping = mapping;
        return this;
    }
    private XQueryMerger setLoadedFiles(List<String> loadedFiles) {
        this.loadedFiles = loadedFiles;
        return this;
    }
    
    private XQueryMerger setFilter(AppenderFilter filter) {
        this.filter=filter;
        return this;
    }
    
    protected StringBuilder _merge() throws ParsingException {
        try {
            if(mapping==null) {
                mapping = new NamespaceMapping();
            }
            // on charge avec l'encoding déterminé
            File f = new File(new URI(source.getSystemId()));
            InputStreamReader isr = null;
            BufferedReader reader = null;
            try {
                isr = new InputStreamReader(new FileInputStream(f), charset);
                reader = new BufferedReader(isr);
                StringBuilder result = filter==null ? new StringBuilder() : new StringBuilder(filter);
                if(loadedFiles==null) {
                    loadedFiles = new ArrayList<String>();
                }
                loadedFiles.add(source.getSystemId());
                parse(reader, result);
                return result;
            } catch(ParsingException ex) {
                throw ex;
            } catch(IOException ex) {
                throw new ParsingException(ex);
            } finally {
                if(reader!=null) try { reader.close(); } catch(Throwable ignore) {}
            }
        } catch(URISyntaxException ex) {
            throw new ParsingException(ex);
        }
    }
    
    /**
     * Permet de récupérer la requête complète, avec tous les imports résolus
     * @return
     * @throws ParsingException 
     */
    public String merge() throws ParsingException {
        StringBuilder sb = _merge();
        if(mainQuery) {
            // il faut remettre les déclarations d'encoding et de namespace
            java.lang.StringBuilder ret = new java.lang.StringBuilder();
            ret.append("xquery encoding '").append(charset.name()).append("';").append(CR).append(CR);
            for(String prefix:mapping.getAllPrefixes()) {
                ret.append("declare namespace ").append(prefix).append("='").append(mapping.getNamespace(prefix)).append("';").append(CR);
            }
            ret.append(CR);
            ret.append(sb.toString());
            return ret.toString();
        } else {
            return sb.toString();
        }
    }
    
    protected StringBuilder parse(BufferedReader reader, StringBuilder result) throws IOException, ParsingException {
        String line = reader.readLine();
        while(line!=null) {
            String clean = ParsingRegex.NORMALIZE_SPACE.matcher(line.trim()).replaceAll(" ").replaceAll("\t", " ");
            if(ParsingRegex.ENCODING_LINE.matcher(clean).matches()) {
                Matcher m = ParsingRegex.QUOTE_EXTRACT.matcher(clean);
                if(m.find()) {
                    String readEncoding = m.group(1);
                    Charset c = Charset.forName(readEncoding);
                    if(!charset.equals(c)) {
                        // on relance le traitement avec le nouvel encoding sur la même source
                        reader.close();
                        System.out.println("reopening "+source.getSystemId()+" with encoding "+c.name());
                        return new XQueryMerger(source, c, uriResolver)._merge();
                    }
                }
            } else if(ParsingRegex.DECLARE_NAMESPACE_LINE.matcher(clean).matches()) {
                Matcher m1 = ParsingRegex.PREFIX_EXTRACT.matcher(clean);
                Matcher m2 = ParsingRegex.NAMESPACE_EXTRACT.matcher(clean);
                if(m1.find() && m2.find()) {
                    String prefix = m1.group(1);
                    String uri = m2.group(1);
                    if(mapping.addMapping(prefix, uri)) {
//                        result.append("declare namespace ").append(prefix).append("=\"").append(uri).append("\";").append(CR);
                    }
                }
            } else if(ParsingRegex.IMPORT_MODULE_LINE.matcher(clean).matches()) {
                Matcher m1 = ParsingRegex.PREFIX_EXTRACT.matcher(clean);
                Matcher m2 = ParsingRegex.NAMESPACE_EXTRACT.matcher(clean);
                Matcher m3 = ParsingRegex.MODULE_URL_EXTRACT.matcher(clean);
                if(m1.find() && m2.find() && m3.find()) {
                    String prefix = m1.group(1);
                    String uri = m2.group(1);
                    String url = m3.group(1);
                    try {
                        StreamSource module = (StreamSource)uriResolver.resolve(url, source.getSystemId());
                        if(!loadedFiles.contains(module.getSystemId())) {
                            XQueryMerger moduleMerger = new XQueryMerger(module, uriResolver).setNamespaceMapping(mapping).setLoadedFiles(loadedFiles).setFilter(new AppenderFilter() {
                                @Override
                                public boolean accept(String s) {
                                    return !s.matches("\\p{Space}*xquery.*");
                                }
                            });
                            result.append("(: code imported from ").append(url).append(" :)").append(CR);
                            result.append(moduleMerger.merge());
                            result.append("(: end import from ").append(url).append(" :)").append(CR);
                        }
                        mapping.addMapping(prefix, uri);
                    } catch(TransformerException ex) {
                        // le fichier n'a pas été trouvé, on laisse l'import
                        // cela référence un module installé dans la base de données
                        // et non fournit dans les sources
                        result.append(line).append(CR);
                    }
                }
            } else {
                result.append(line).append(CR);
            }
            line=reader.readLine();
        }
        reader.close();
        return result;
    }
    
    /**
     * Renvoie l'encoding du fichier traité
     * @return 
     */
    public Charset getEncoding() {
        return charset;
    }
    public static void main(String[] args) {
        if(args.length!=1) {
            System.err.println("java "+XQueryMerger.class.getName()+" <fileToMerge.xq>");
            System.exit(1);
        } else {
            File f = new File(args[0]);
            if(f.exists() && f.isFile()) {
                StreamSource source = new StreamSource(f);
                XQueryMerger merger = new XQueryMerger(source);
                merger.setMainQuery();
                try {
                    System.out.println(merger.merge());
                } catch(Exception ex) {
                    ex.printStackTrace(System.err);
                    System.exit(2);
                }
            } else {
                System.err.println("Unable to locate "+args[0]+" or it is not a regular file");
                System.exit(1);
            }
        }
    }
    
}
