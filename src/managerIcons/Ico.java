package managerIcons;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;

import javax.imageio.ImageIO;

import offensiveUtils.Require;
import tools.Fs;
import utils.Streams;

public final class Ico{
  private Ico(){}
  public static final List<Integer> standardSizes= List.of(16,24,32,48,64,128,256);
  public static void writeResized(Path sourcePng, Path outIco, List<Integer> sizes){
    var source= read(sourcePng);
    writeFrames(sizes.stream().map(size->resize(source,size)).toList(), outIco);
  }
  public static void write(List<Path> sourcePngs, Path outIco){
    writeFrames(sourcePngs.stream().map(Ico::read).toList(), outIco);
  }
  private static void writeFrames(List<BufferedImage> frames, Path outIco){
    Require.check(!frames.isEmpty(), "Need at least one icon frame");
    var sorted= frames.stream().sorted(Comparator.comparingInt(BufferedImage::getWidth)).toList();
    var pngs= sorted.stream().map(Ico::encodePng).toList();
    Fs.ofV(()->Files.write(outIco, assemble(sorted,pngs), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
  }
  private static byte[] assemble(List<BufferedImage> frames, List<byte[]> pngs){
    var headerSize= 6+16*frames.size();
    var out= new ByteArrayOutputStream();
    u16(out,0);
    u16(out,1);
    u16(out,frames.size());
    Streams.zip(frames,pngs).fold((offset,frame,png)->entry(out,frame,png.length,offset), headerSize);
    pngs.forEach(png->out.writeBytes(png));
    return out.toByteArray();
  }
  private static int entry(ByteArrayOutputStream out, BufferedImage frame, int pngLength, int offset){
    Require.check(frame.getWidth() == frame.getHeight(), "Icon frames must be square: "+frame.getWidth()+"x"+frame.getHeight());
    Require.check(frame.getWidth() <= 256, "Icon frames cannot exceed 256px: "+frame.getWidth());
    var side= frame.getWidth();
    out.write(side == 256 ? 0 : side);
    out.write(side == 256 ? 0 : side);
    out.write(0);
    out.write(0);
    u16(out,1);
    u16(out,32);
    u32(out,pngLength);
    u32(out,offset);
    return offset+pngLength;
  }
  private static void u16(ByteArrayOutputStream out, int v){
    out.write(v&0xFF);
    out.write((v>>>8)&0xFF);
  }
  private static void u32(ByteArrayOutputStream out, int v){
    out.write(v&0xFF);
    out.write((v>>>8)&0xFF);
    out.write((v>>>16)&0xFF);
    out.write((v>>>24)&0xFF);
  }
  private static BufferedImage resize(BufferedImage source, int size){
    var res= new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
    var g= res.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
    g.drawImage(source,0,0,size,size,null);
    g.dispose();
    return res;
  }
  private static byte[] encodePng(BufferedImage frame){
    var out= new ByteArrayOutputStream();
    try{ ImageIO.write(frame,"png",out); }
    catch(IOException e){ throw new UncheckedIOException(e); }
    return out.toByteArray();
  }
  private static BufferedImage read(Path png){ return (BufferedImage)FolderIcon.read(png); }
}