package at.hausgarten.service;

import java.io.ByteArrayOutputStream;

/** Bounded EXIF parsing; no location metadata is copied to a new JPEG. */
final class PhotoPolicy {
    static int edge(String quality, boolean profile) {
        return profile ? 1024 : "high".equals(quality) ? 4096 : 3072;
    }
    static int orientation(byte[] b) {
        try {
            if(b.length>=8&&b[0]==(byte)137&&b[1]=='P'){
                for(int p=8;p+12<=b.length;){long n=u32(b,p,false);if(n>b.length-p-12)break;if(b[p+4]=='e'&&b[p+5]=='X'&&b[p+6]=='I'&&b[p+7]=='f')return tiff(b,p+8,p+8+(int)n);p+=12+(int)n;}
            }
            if(b.length>=12&&b[0]=='R'&&b[1]=='I'){
                for(int p=12;p+8<=b.length;){long n=u32(b,p+4,true);if(n>b.length-p-8)break;if(b[p]=='E'&&b[p+1]=='X'&&b[p+2]=='I'&&b[p+3]=='F'){int t=p+8;if(n>=6&&b[t]=='E'&&b[t+1]=='x')t+=6;return tiff(b,t,p+8+(int)n);}p+=8+(int)n+((int)n&1);}
            }
            if(b.length<4||(b[0]&255)!=255||(b[1]&255)!=216)return 1;
            for(int p=2;p+4<b.length;){
                if((b[p]&255)!=255)break;
                int marker=b[p+1]&255;if(marker==218||marker==217)break;
                int n=((b[p+2]&255)<<8)|(b[p+3]&255);
                if(n<2||p+2+n>b.length)break;
                if(marker==225&&n>=16&&b[p+4]=='E'&&b[p+5]=='x'&&b[p+6]=='i'&&b[p+7]=='f') {
                    return tiff(b,p+10,p+2+n);
                }
                p+=2+n;
            }
        }catch(IndexOutOfBoundsException invalid){return 1;}
        return 1;
    }
    private static int tiff(byte[] b,int t,int end){
        if(t+8>end)return 1;boolean little=b[t]=='I'&&b[t+1]=='I';if(!little&&!(b[t]=='M'&&b[t+1]=='M'))return 1;long offset=u32(b,t+4,little);if(offset<8||offset>end-t-2)return 1;int d=t+(int)offset,count=u16(b,d,little);
        for(int j=0;j<Math.min(count,1024);j++){int q=d+2+j*12;if(q+12>end)break;if(u16(b,q,little)==274&&u16(b,q+2,little)==3&&u32(b,q+4,little)==1){int v=u16(b,q+8,little);return v>=1&&v<=8?v:1;}}return 1;
    }
    static byte[] withoutMetadata(byte[] b,String mime){
        if(mime.equals("image/jpeg"))return withoutJpegMetadata(b);
        ByteArrayOutputStream out=new ByteArrayOutputStream(b.length);
        if(mime.equals("image/png")&&b.length>=8){out.write(b,0,8);int p=8;while(p+12<=b.length){long n=u32(b,p,false);if(n>b.length-p-12)return b;String type=new String(b,p+4,4,java.nio.charset.StandardCharsets.US_ASCII);if(!java.util.Arrays.asList("eXIf","tEXt","iTXt","zTXt").contains(type))out.write(b,p,12+(int)n);p+=12+(int)n;}if(p!=b.length)return b;return out.toByteArray();}
        if(mime.equals("image/webp")&&b.length>=12){out.write(b,0,12);int p=12;while(p+8<=b.length){long n=u32(b,p+4,true);if(n>b.length-p-8)return b;String type=new String(b,p,4,java.nio.charset.StandardCharsets.US_ASCII);int size=8+(int)n+((int)n&1);if(p+size>b.length)return b;if(!type.equals("EXIF")&&!type.equals("XMP ")){byte[] chunk=java.util.Arrays.copyOfRange(b,p,p+size);if(type.equals("VP8X")&&n>=1)chunk[8]&=~12;out.write(chunk,0,chunk.length);}p+=size;}if(p!=b.length)return b;byte[] result=out.toByteArray();int size=result.length-8;for(int i=0;i<4;i++)result[4+i]=(byte)(size>>>(8*i));return result;}
        return b;
    }
    private static int u16(byte[] b,int p,boolean l){return l?(b[p]&255)|((b[p+1]&255)<<8):((b[p]&255)<<8)|(b[p+1]&255);}
    private static long u32(byte[] b,int p,boolean l){return l?(u16(b,p,l)&65535L)|((long)u16(b,p+2,l)<<16):((long)u16(b,p,l)<<16)|(u16(b,p+2,l)&65535L);}
    static byte[] withoutJpegMetadata(byte[] b) {
        if(b.length<4||(b[0]&255)!=255||(b[1]&255)!=216)return b;
        ByteArrayOutputStream out=new ByteArrayOutputStream(b.length);out.write(b,0,2);
        int p=2;
        while(p+4<=b.length&&(b[p]&255)==255){
            int m=b[p+1]&255;if(m==218||m==217)break;
            int n=((b[p+2]&255)<<8)|(b[p+3]&255);if(n<2||p+2+n>b.length)return b;
            // Strip EXIF/XMP/IPTC/comments, retain color profiles and coding tables.
            if(m!=225&&m!=237&&m!=254)out.write(b,p,n+2);p+=n+2;
        }
        out.write(b,p,b.length-p);return out.toByteArray();
    }
}
