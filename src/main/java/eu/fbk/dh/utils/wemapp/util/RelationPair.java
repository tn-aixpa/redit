package eu.fbk.dh.utils.wemapp.util;

public class RelationPair {
    String label;
    Integer entity1;
    Integer entity2;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getEntity1() {
        return entity1;
    }

    public void setEntity1(Integer entity1) {
        this.entity1 = entity1;
    }

    public Integer getEntity2() {
        return entity2;
    }

    public void setEntity2(Integer entity2) {
        this.entity2 = entity2;
    }

    public RelationPair(String label, Integer entity1, Integer entity2) {
        this.label = label;
        this.entity1 = entity1;
        this.entity2 = entity2;
    }

    @Override
    public String toString() {
        return "RelationPair{" +
                "label='" + label + '\'' +
                ", entity1=" + entity1 +
                ", entity2=" + entity2 +
                '}';
    }
}
