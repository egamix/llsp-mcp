package com.luciadmcp.knowledge.catalog.sqlite;

import com.luciadmcp.knowledge.domain.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** schema/2: bounded big-endian UTF-8 field frames, never Java object serialization. */
final class DocumentationCodec {
    static final int MAX_ENCODED_BYTES = DocumentationPayload.MAX_BYTES + 4 * 65536 + 4;
    private DocumentationCodec() {}

    static byte[] encode(List<String> fields) {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            if (fields.size() > 65536) throw new IllegalArgumentException("PAYLOAD_TOO_LARGE");
            out.writeInt(fields.size());
            for (String field : fields) {
                byte[] value = field == null ? null : field.getBytes(StandardCharsets.UTF_8);
                if (value != null && value.length > DocumentationPayload.MAX_BYTES) throw new IllegalArgumentException("PAYLOAD_TOO_LARGE");
                out.writeInt(value == null ? -1 : value.length);
                if (value != null) out.write(value);
                if (bytes.size() > MAX_ENCODED_BYTES) throw new IllegalArgumentException("PAYLOAD_TOO_LARGE");
            }
            return bytes.toByteArray();
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    static List<String> decode(byte[] bytes) {
        if (bytes == null || bytes.length > MAX_ENCODED_BYTES) throw new IllegalArgumentException("PAYLOAD_TOO_LARGE");
        try {
            var in = new DataInputStream(new ByteArrayInputStream(bytes));
            int count = in.readInt();
            if (count < 0 || count > 65536 || count > in.available()/4) throw new IllegalArgumentException("CORRUPT_CONTENT");
            List<String> fields = new ArrayList<>();
            for (int i=0;i<count;i++) {
                int length = in.readInt();
                if (length < -1 || length > in.available()) throw new IllegalArgumentException("CORRUPT_CONTENT");
                fields.add(length == -1 ? null : new String(in.readNBytes(length), StandardCharsets.UTF_8));
            }
            if (in.available()!=0 || !Arrays.equals(bytes,encode(fields))) throw new IllegalArgumentException("CORRUPT_CONTENT");
            return fields;
        } catch (IOException e) { throw new IllegalArgumentException("CORRUPT_CONTENT",e); }
    }

    static byte[] section(DocumentationPayload.Section section) { return encode(DocumentationPayload.canonicalFields(List.of(section))); }
    static DocumentationPayload.Section section(byte[] bytes) {
        Cursor c = new Cursor(decode(bytes));
        if (c.count(256)!=1) throw new IllegalArgumentException("CORRUPT_CONTENT");
        String id=c.next(), title=c.next(); SourceLocator locator=c.locator();
        int count=c.count(DocumentationPayload.MAX_BLOCKS);
        List<DocumentationPayload.Block> blocks=new ArrayList<>();
        for(int i=0;i<count;i++) {
            Optional<DocumentationPayload.Table> table=Optional.empty();
            String marker=c.next();
            if(marker.equals("table")) {
                int width=c.count(65536); List<String> headers=c.strings(width);
                int rows=c.count(65536); List<List<String>> values=new ArrayList<>();
                if ((long)width*rows>65536) throw new IllegalArgumentException("CORRUPT_CONTENT");
                for(int r=0;r<rows;r++) values.add(c.strings(width));
                table=Optional.of(new DocumentationPayload.Table(headers,values));
            } else if(!marker.equals("absent")) throw new IllegalArgumentException("CORRUPT_CONTENT");
            String bool=c.next(); if(!bool.equals("true")&&!bool.equals("false")) throw new IllegalArgumentException("CORRUPT_CONTENT");
            boolean ordered=Boolean.parseBoolean(bool); var items=c.items(0);
            var kind=DocumentationPayload.BlockKind.valueOf(c.next()); String label=c.next(),text=c.next();
            int links=c.count(65536); List<DocumentationPayload.Link> refs=new ArrayList<>();
            for(int l=0;l<links;l++) refs.add(new DocumentationPayload.Link(c.next(),c.next(),c.next(),c.locator()));
            blocks.add(new DocumentationPayload.Block(kind,label,text,refs,table,items,ordered));
        }
        if(c.index!=c.fields.size()) throw new IllegalArgumentException("CORRUPT_CONTENT");
        var section=new DocumentationPayload.Section(id,title,locator,blocks);
        if(!Arrays.equals(bytes,section(section))) throw new IllegalArgumentException("CORRUPT_CONTENT");
        return section;
    }

    private static final class Cursor {
        final List<String> fields; int index;
        Cursor(List<String> fields) { this.fields=fields; }
        String next() { if(index>=fields.size()) throw new IllegalArgumentException("CORRUPT_CONTENT"); return fields.get(index++); }
        int count(int max) { int n=Integer.parseInt(next()); if(n<0||n>max||n>fields.size()-index) throw new IllegalArgumentException("CORRUPT_CONTENT"); return n; }
        List<String> strings(int count) { List<String> out=new ArrayList<>();for(int i=0;i<count;i++)out.add(next());return out; }
        SourceLocator locator() {
            var kind=SourceLocator.Kind.valueOf(next()); String path=next(),anchor=next(),reason=next(),lines=next(),section=next();
            Optional<SourceLocator.LineRange> range=Optional.empty();
            if(lines!=null) { String[] parts=lines.split(":",-1);if(parts.length!=2)throw new IllegalArgumentException("CORRUPT_CONTENT");range=Optional.of(new SourceLocator.LineRange(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]))); }
            return new SourceLocator(kind,path,Optional.ofNullable(anchor),reason,range,Optional.ofNullable(section));
        }
        List<DocumentationPayload.ListItem> items(int depth) {
            if(depth>32)throw new IllegalArgumentException("PAYLOAD_TOO_LARGE");
            int n=count(65536); List<DocumentationPayload.ListItem> result=new ArrayList<>();
            for(int i=0;i<n;i++)result.add(new DocumentationPayload.ListItem(next(),items(depth+1)));
            return result;
        }
    }
}
