package dev.itemcontent.definition;
import java.util.*;
/** Detect config alias cycles before the model loader sees them; no recursion depth limit. */
public final class ModelReferences {
    private ModelReferences() {}
    public static void validate(Map<String,String> aliases) {
        Set<String> done=new HashSet<>();
        for(String start:aliases.keySet()) {
            Set<String> path=new LinkedHashSet<>(); String next=start;
            while(next!=null && aliases.containsKey(next) && !done.contains(next)) {
                if(!path.add(next))throw new IllegalArgumentException("Циклические ссылки моделей: "+String.join(" → ",path)+" → "+next);
                next=aliases.get(next);
            }
            done.addAll(path);
        }
    }
}
