package littlejlib.proj;

public class Organization {
    private String name;
    private String groupId;

    public Organization() {
    }

    public Organization(String name, String groupId) {
        this.name = name;
        this.groupId = groupId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
}
