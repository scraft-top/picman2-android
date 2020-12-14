package top.scraft.picman2.server.data;

import java.util.Set;

import lombok.Data;

@Data
public class PictureDetail {

    private int accessLid;
    private String pid;
    private String description;
    private Set<String> tags;
    private long fileSize;
    private int width;
    private int height;
    private long createTime;
    private long lastModify;
    private String creator;

}
