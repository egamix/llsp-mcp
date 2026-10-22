package com.luciadmcp.knowledge.ingest.javadoc;

import com.luciadmcp.knowledge.domain.*;
import org.jsoup.nodes.*;
import java.util.*;

/** Offline, owner-scoped extraction. Unsupported DTO shapes retain HTML evidence and a partial issue. */
final class OwnedDocumentationExtractor {
    private final String path;
    private final List<String> issues;
    private final List<DocumentationPayload.Block> blocks=new ArrayList<>();
    private int visited;
    private long bytes;
    private boolean apiOwner=true;
    private Element signature;
    private Element generatedMemberHeading;
    private OwnedDocumentationExtractor(String path,List<String> issues) { this.path=path;this.issues=issues; }
    static List<DocumentationPayload.Block> extract(Element owner,String path,List<String> issues) {
        if(owner==null)return List.of();
        var parser=new OwnedDocumentationExtractor(path,issues);
        Element signature=JavadocFieldExtractor.firstOwned(owner,".type-signature,.typeSignature,.member-signature,.memberSignature");
        if(signature==null)signature=JavadocFieldExtractor.firstOwned(owner,"pre:not(.block pre)");
        if(signature!=null && signature.closest(".block")==null) {
            parser.signature=signature;
            if(owner.is("section.detail,li.blockList,li.blockListLast")) {
                for(Element child:owner.children()) {
                    if(child==signature || child.getAllElements().contains(signature))break;
                    if(parser.generatedMemberHeading==null && child.tagName().equals(owner.is("section.detail")?"h3":"h4"))parser.generatedMemberHeading=child;
                }
            }
            parser.add(new DocumentationPayload.Block(DocumentationPayload.BlockKind.CODE,"signature",signature.tagName().equals("pre")?signature.wholeText():signature.text(),parser.links(signature)));
        }
        parser.walk(owner,owner,"description",0);
        return List.copyOf(parser.blocks);
    }
    static List<DocumentationPayload.Block> narrative(Element owner,String path,List<String> issues) {
        var parser=new OwnedDocumentationExtractor(path,issues);parser.apiOwner=false;parser.walk(owner,owner,"description",0);return List.copyOf(parser.blocks);
    }
    private void guard(int depth) {
        if(depth>32 || ++visited>65536)throw new IllegalArgumentException("PAYLOAD_TOO_LARGE: owned documentation "+path);
    }
    private void add(DocumentationPayload.Block block) {
        bytes+=block.text().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if(blocks.size()>=DocumentationPayload.MAX_BLOCKS || bytes>DocumentationPayload.MAX_BYTES)throw new IllegalArgumentException("PAYLOAD_TOO_LARGE: "+path);
        blocks.add(block);
    }
    private boolean foreign(Element el,Element owner) {
        return el!=owner && (el.is("section.detail, nav, .summary, .details, .member-summary, .memberSummary, .overviewSummary, .deprecated-label, .deprecatedLabel, .deprecation-comment, .deprecationComment, header, footer, script, style") || el.is("li.blockList, li.blockListLast") && el.children().stream().anyMatch(c->c.tagName().equals("h4")));
    }
    private void walk(Element el,Element owner,String label,int depth) {
        guard(depth);if(foreign(el,owner))return;
        if(el==signature)return;
        if(el!=owner && el.is("h1,h2,h3,h4,h5,h6")) {
            boolean memberHeading=el==generatedMemberHeading;
            if(!memberHeading)unsupported(el,"authored heading "+el.tagName());
            return;
        }
        if(el!=owner && (el.is(".deprecation-block,.deprecationBlock") || el.hasClass("block") && el.selectFirst(".deprecated-label,.deprecatedLabel")!=null)) {
            Element value=el.clone();value.select(".deprecated-label,.deprecatedLabel").remove();
            for(Element comment:value.select(".deprecation-comment,.deprecationComment"))comment.removeAttr("class");
            walk(value,value,"deprecated",depth+1);return;
        }
        if(el.tagName().equals("dl")) { notes(el,owner,depth);return; }
        if(el.tagName().equals("pre")) { add(new DocumentationPayload.Block(DocumentationPayload.BlockKind.CODE,label,el.wholeText(),links(el)));return; }
        if(el.tagName().equals("table")) { table(el,label,depth);return; }
        if((el.tagName().equals("ul") || el.tagName().equals("ol")) && !el.hasClass("blockList") && !el.hasClass("blockListLast") && !el.hasClass("member-list")) { list(el,label);return; }
        if(el.is("img,svg,iframe,video,audio,canvas,object,input,button,select,textarea,math")) { unsupported(el,"rich element "+el.tagName());return; }
        // Flush adjacent inline nodes as a paragraph, splitting at structural children in source order.
        Element inline=new Element("span");
        for(Node node:el.childNodes()) {
            guard(depth+1);
            if(node instanceof Element child && (child.isBlock() || child.is("img,svg,iframe,video,audio,canvas,object,input,button,select,textarea,math") || child.selectFirst("img,svg,iframe,video,audio,canvas,object,input,button,select,textarea,math")!=null)) {
                flush(inline,label);walk(child,owner,label,depth+1);
            } else if(node instanceof Element child && (foreign(child,owner))) {
                flush(inline,label);
            } else inline.appendChild(node.clone());
        }
        flush(inline,label);
    }
    private void flush(Element inline,String label) {
        String text=inline.text();
        if(!text.isBlank())add(new DocumentationPayload.Block(noteLabel(label)?DocumentationPayload.BlockKind.NOTE:DocumentationPayload.BlockKind.PARAGRAPH,label,text,links(inline)));
        inline.empty();
    }
    private static boolean noteLabel(String label) { return Set.of("API Note","Implementation Note","Implementation Requirements","deprecated").contains(label); }
    private boolean generatedNotes(Element dl) {
        if(dl.selectFirst("dt a[href]")!=null)return false;
        boolean hasDefinition=false;
        for(Element child:dl.children()) {
            if(child.tagName().equals("dt")) { if(!hasDefinition && child!=dl.children().first())return false;hasDefinition=false; }
            else if(child.tagName().equals("dd")) { if(child==dl.children().first())return false;hasDefinition=true; }
            else return false;
        }
        if(!hasDefinition)return false;
        if(dl.hasClass("notes"))return true;
        // Legacy API notes have known tag labels; narrative prose requires the doclet's label markup.
        if(!apiOwner && dl.selectFirst("dt > .simpleTagLabel,dt > .paramLabel,dt > .returnLabel,dt > .throwsLabel,dt > .seeLabel") == null)return false;
        var labels=dl.children().stream().filter(el->el.tagName().equals("dt")).toList();
        return !labels.isEmpty() && labels.stream().allMatch(el->Set.of("parameters","type parameters","returns","throws","exceptions","since","author","version","serial","serial data","serial field","see also","overrides","specified by","api note","implementation note","implementation requirements").contains(el.text().replaceFirst(":\\s*$","").trim().toLowerCase(Locale.ROOT)));
    }
    private void notes(Element dl,Element owner,int depth) {
        // An author definition list is richer than our DTO; retain explicit evidence.
        if(dl.closest(".block")!=null || !generatedNotes(dl)) { unsupported(dl,"definition list");return; }
        String label=null;
        for(Element child:dl.children()) {
            if(child.tagName().equals("dt"))label=child.text().replaceFirst(":\\s*$","").trim();
            else if(child.tagName().equals("dd") && label!=null) {
                String normalized=label.toLowerCase(Locale.ROOT);
                String stored=switch(normalized) { case "api note"->"API Note";case "implementation note"->"Implementation Note";case "implementation requirements"->"Implementation Requirements";default->normalized; };
                Element value=child.clone();List<DocumentationPayload.Link> prefixLinks=List.of();
                if(Set.of("parameters","type parameters","throws","exceptions").contains(normalized)) {
                    Element name=value.selectFirst("code");
                    if(name==null) { unsupported(child,"unnamed "+label);continue; }
                    stored=(normalized.equals("parameters")?"parameter ":normalized.equals("type parameters")?"type parameter ":"throws ")+name.text();
                    prefixLinks=links(name);name.remove();
                    if(!value.childNodes().isEmpty() && value.childNode(0) instanceof TextNode text)text.text(text.getWholeText().replaceFirst("^\\s*-\\s*",""));
                }
                int before=blocks.size();walk(value,value,stored,depth+1);
                if(!prefixLinks.isEmpty() && blocks.size()>before) {
                    var first=blocks.get(before);var all=new ArrayList<>(prefixLinks);all.addAll(first.links());
                    blocks.set(before,new DocumentationPayload.Block(first.kind(),first.label(),first.text(),all,first.table(),first.items(),first.ordered()));
                }
                if(before==blocks.size())add(new DocumentationPayload.Block(noteLabel(stored)?DocumentationPayload.BlockKind.NOTE:DocumentationPayload.BlockKind.PARAGRAPH,stored,"",prefixLinks));
            } else if(!child.tagName().equals("dt"))unsupported(child,"ambiguous note ownership");
        }
    }
    private void table(Element el,String label,int depth) {
        // Admit every descendant before selector/text traversal, including inline-only cells.
        Node node=el;int relativeDepth=0;
        while(true) {
            guard(depth+relativeDepth);
            if(node.firstChild()!=null) { node=node.firstChild();relativeDepth++;continue; }
            while(node!=el && node.nextSibling()==null) { node=node.parent();relativeDepth--; }
            if(node==el)break;
            node=node.nextSibling();
        }
        var rows=el.select("tr");
        boolean unsupported=el.selectFirst("caption")!=null || rows.isEmpty() || !el.select("[rowspan],[colspan],[scope=row],[scope=rowgroup],table table").isEmpty();
        List<String> headers=new ArrayList<>();List<List<String>> values=new ArrayList<>();
        for(int i=0;i<rows.size();i++) {
            guard(depth+1);
            var cells=rows.get(i).children();List<String> row=new ArrayList<>();
            if(cells.isEmpty())unsupported=true;
            for(Element cell:cells) {
                guard(depth+2);
                if(!cell.tagName().equals(i==0?"th":"td") || !cell.select("p,pre,ul,ol,dl,table,img,svg,br,input,button,select,textarea,math,video,audio,canvas,object,iframe").isEmpty())unsupported=true;
                row.add(cell.text());
            }
            if(i==0)headers=row;else { if(row.size()!=headers.size())unsupported=true;values.add(row); }
        }
        if(unsupported) { unsupported(el,"table spans, row headers, ragged rows or rich cells");return; }
        add(new DocumentationPayload.Block(DocumentationPayload.BlockKind.TABLE,label,"",links(el),Optional.of(new DocumentationPayload.Table(headers,values)),List.of(),false));
    }
    private void list(Element el,String label) {
        try {
            var items=items(el,el.tagName(),0);
            add(new DocumentationPayload.Block(DocumentationPayload.BlockKind.LIST,label,"",links(el),Optional.empty(),items,el.tagName().equals("ol")));
        } catch(UnsupportedOperationException e) { unsupported(el,e.getMessage()); }
    }
    private List<DocumentationPayload.ListItem> items(Element list,String order,int depth) {
        guard(depth);if(!list.tagName().equals(order) || list.hasAttr("start") || list.hasAttr("reversed") || list.hasAttr("type"))throw new UnsupportedOperationException("mixed-order or attributed nested list");
        var result=new ArrayList<DocumentationPayload.ListItem>();
        for(Element li:list.children()) {
            guard(depth+1);if(!li.tagName().equals("li") || li.hasAttr("value"))throw new UnsupportedOperationException("rich list child");
            Element prose=li.clone();var children=new ArrayList<DocumentationPayload.ListItem>();boolean nested=false;
            for(Element child:li.children()) {
                if(child.tagName().equals("ul")||child.tagName().equals("ol")) { if(nested)throw new UnsupportedOperationException("multiple nested lists");nested=true;children.addAll(items(child,order,depth+1)); }
                else if(nested || child.isBlock() || !child.select("ul,ol,table,pre,br,img,svg,input,button,select,textarea,math,video,audio,canvas,object,iframe").isEmpty())throw new UnsupportedOperationException("rich list item or text after nested list");
            }
            for(Node child:li.childNodes())if(nested && child instanceof TextNode text && !text.isBlank() && child.siblingIndex()>li.children().stream().filter(c->c.is("ul,ol")).findFirst().orElseThrow().siblingIndex())throw new UnsupportedOperationException("text after nested list");
            prose.select("ul,ol").remove();result.add(new DocumentationPayload.ListItem(prose.text(),children));
        }
        return List.copyOf(result);
    }
    private void unsupported(Element el,String reason) {
        issues.add(path+": PARTIAL_UNSUPPORTED: "+reason+" at "+el.tagName()+(el.id().isEmpty()?"":"#"+el.id()));
        add(new DocumentationPayload.Block(DocumentationPayload.BlockKind.CODE,"unsupported HTML",el.outerHtml(),links(el)));
    }
    private List<DocumentationPayload.Link> links(Element el) {
        var result=new ArrayList<DocumentationPayload.Link>();
        for(Element a:el.select("a[href]")) {
            guard(0);String href=a.attr("href");var ref=JavadocFieldExtractor.makeRef(a.text(),href,path);SourceLocator locator;
            try {
                locator=switch(ref.locatorKind()) {
                    case LOCAL -> new SourceLocator(SourceLocator.Kind.SOURCE,ref.relativePath(),Optional.ofNullable(ref.anchor().isEmpty()?null:ref.anchor()),"");
                    case EXTERNAL -> new SourceLocator(SourceLocator.Kind.EXTERNAL,href,Optional.empty(),"external href; not fetched");
                    case ROOT_ESCAPE -> new SourceLocator(SourceLocator.Kind.ABSENT,"",Optional.empty(),"href escapes source root");
                    case NONE -> new SourceLocator(SourceLocator.Kind.ABSENT,"",Optional.empty(),"empty href");
                };
            } catch(IllegalArgumentException e) { locator=new SourceLocator(SourceLocator.Kind.ABSENT,"",Optional.empty(),"unsupported local href"); }
            result.add(new DocumentationPayload.Link(a.text(),href,"",locator));
        }
        return List.copyOf(result);
    }
}
