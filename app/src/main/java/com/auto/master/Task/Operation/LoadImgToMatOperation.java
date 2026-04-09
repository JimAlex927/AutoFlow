package com.auto.master.Task.Operation;

/**
 * 这个operation：  把项目下的 img转换成 mat存储到内存 也就是 template的 cache
 * 从而后续识别可以防止io操作 提高速度
 *
 */
public class LoadImgToMatOperation extends MetaOperation{

    public LoadImgToMatOperation() {

        this.setType(4);
    }
}
