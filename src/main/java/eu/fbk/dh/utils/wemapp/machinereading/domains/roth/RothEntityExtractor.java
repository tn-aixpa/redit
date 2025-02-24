package eu.fbk.dh.utils.wemapp.machinereading.domains.roth;

import eu.fbk.dh.utils.wemapp.machinereading.BasicEntityExtractor;
import eu.fbk.dh.utils.wemapp.machinereading.structure.EntityMentionFactory;

import java.util.HashMap;
import java.util.Map;

public class RothEntityExtractor extends BasicEntityExtractor {
    private static final long serialVersionUID = 1L;

    public static final boolean USE_SUB_TYPES = false;

    private Map<String, String> entityTagForNer;

    public RothEntityExtractor() {
        super(null, USE_SUB_TYPES, null, true, new EntityMentionFactory(), true);
        entityTagForNer = new HashMap<>();
//    entityTagForNer.put("person", "Peop");
//    entityTagForNer.put("organization", "Org");
//    entityTagForNer.put("location", "Loc");
        entityTagForNer.put("person", "PEOPLE");
        entityTagForNer.put("organization", "ORGANIZATION");
        entityTagForNer.put("location", "LOCATION");

    }

    @Override
    public String getEntityTypeForTag(String ner) {
        ner = ner.toLowerCase();
        if (entityTagForNer.containsKey(ner))
            return entityTagForNer.get(ner);
        else
            return "O";
    }

}
