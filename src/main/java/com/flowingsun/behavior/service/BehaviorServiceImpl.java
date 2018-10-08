package com.flowingsun.behavior.service;


import com.flowingsun.article.entity.Article;
import com.flowingsun.article.vo.CategoryArticleQuery;
import com.flowingsun.behavior.dao.CommentMapper;
import com.flowingsun.behavior.dao.PictureMapper;
import com.flowingsun.behavior.dao.ThankMapper;
import com.flowingsun.behavior.entity.BehaviorStatus;
import com.flowingsun.behavior.entity.Comment;
import com.flowingsun.behavior.entity.Picture;
import com.flowingsun.behavior.entity.Thank;
import com.flowingsun.behavior.vo.PictureQuery;
import com.flowingsun.common.dao.RedisDAO;
import com.flowingsun.common.utils.InfoCountUtils;
import com.flowingsun.user.dao.UserMapper;
import com.flowingsun.user.entity.User;
import org.apache.log4j.Logger;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.UnauthenticatedException;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service("behaviorServiceImpl")
public class BehaviorServiceImpl implements BehaviorService {

    private static Logger logger = Logger.getLogger(BehaviorServiceImpl.class);

    private static final Integer SUCCESS=1;

    private static final Integer FAIL=0;

    @Autowired
    private RedisDAO redisDAO;

    @Autowired
    private ThankMapper thankMapper;

    @Autowired
    private PictureMapper pictureMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private UserMapper userMapper;

    /**
     *@Author Lyon[flowingsun007@163.com]
     *@Date 18/09/10 22:26
     *@Param [commentBean, request]
     *@Return java.lang.String
     *@Description setComment
     * 评论文章
     */
    @Override
    public String setComment(Comment commentBean, HttpServletRequest request) {
        try {
            Long userId = (Long)SecurityUtils.getSubject().getSession().getAttribute("userId");
            if (userId != null) {
                commentBean.setUserid(userId);
                if (commentMapper.insertSelective(commentBean).equals(SUCCESS))
                    return "setComment_success";
                else
                    return "setComment_fail_插入数据库失败";
            }else {
                return "setComment_fail_未登录";
            }
        }catch(Exception e){
            e.printStackTrace();
            return "setComment_fail_exception";
        }
    }


    /**
     *@Author Lyon[flowingsun007@163.com]
     *@Date 18/09/10 22:26
     *@Param [thankBean, request]
     *@Return java.lang.String
     *@Description setThank
     * 给文章点感谢
     */
    @Override
    public String setThank (Thank thankBean, HttpServletRequest request){
        try {
            Long userId = (Long)SecurityUtils.getSubject().getSession().getAttribute("userId");
            if(userId!=null){
                thankBean.setUserid(userId.intValue());
                if (thankMapper.selectThankNumByThankbean(thankBean).equals(0)) {
                    thankBean.setThankdate(new Timestamp(new Date().getTime()));
                    if (SUCCESS == thankMapper.insertThank(thankBean)) {
                        return "setThank_success";
                    }
                } else {
                    return "setThank_fail_重复点赞";
                }
            }
            return "setThank_fail_未登录";
        } catch (Exception e) {
            e.printStackTrace();
            return "setThank_fail_exception";
        }
    }

    /**
     *@Author Lyon[flowingsun007@163.com]
     *@Date 18/06/4 19:22
     *@Description saveUserImage
     * 配合imageUpload()使用，用来将上传到服务器的单个图片路径存储到数据库
     */
    @Override
    public boolean saveUserImage (Picture picture){
        if (picture != null) {
            return (pictureMapper.insert(picture) != FAIL) ?  true :  false;
        }
        return false;
    }

    /**
     *@Author Lyon[flowingsun007@163.com]
     *@Date 18/06/4 19:24
     *@Description imageUpload
     * 利用MultipartHttpServletRequest、MultipartFile等，
     * 用来上传文件到服务器存储，同时将文件的路径，用saveUserImage()保存至数据库。
     * @Param request session
     * @Return 传回String类型的消息-resultInfo,用于前端显示。
     */
    @Override
    public String imageUpload (MultipartHttpServletRequest request,String description){
        Long userId = (Long)SecurityUtils.getSubject().getSession().getAttribute("userId");
        if (userId == null) {
            return "用户未登录！";
        }else{
            if(description==null){
                description="";
            }
            String resultInfo = "";
            Date date = new Date();
            Timestamp dateTime = new Timestamp(date.getTime());
            User user = redisDAO.getRedisUser(userId);
            //缓存未命中，从数据库读user
            if(user==null){
                user = userMapper.selectByPrimaryKey(userId);
            }
            try {
                Iterator<String> fileNames = request.getFileNames();
                while (fileNames.hasNext()) {
                    //把fileNames集合中的值打出来
                    String fileName = fileNames.next();
                    /*
                     * request.getFiles(fileName)方法即通过fileName这个Key, 得到对应的文件
                     * 集合列表. 只是在这个Map中, 文件被包装成MultipartFile类型
                     */
                    List<MultipartFile> fileList = request.getFiles(fileName);
                    Picture picture = new Picture();
                    picture.setUserid(userId);
                    picture.setCreatedate(dateTime);
                    //遍历文件列表
                    Iterator<MultipartFile> fileIte = fileList.iterator();
                    while (fileIte.hasNext()) {
                        //获得每一个文件
                        MultipartFile multipartFile = fileIte.next();
                        //获得原文件名
                        String originalFilename = multipartFile.getOriginalFilename();
                        if ("" == originalFilename) {
                            resultInfo += "空文件😶！;";
                            continue;
                        }
                        resultInfo += ("原文件名:" + originalFilename + ";");
                        //String rootPath = "/Users/zhaoluyang/JavaProject/LyonBlog/src/main/webapp";
                        String contextPath = "/static/userFile/images/";
                        String path = request.getSession().getServletContext().getRealPath(contextPath);
                        //检查该路径对应的目录是否存在. 如果不存在则创建目录
                        File dir = new File(path);
                        if (!dir.exists()) {
                            dir.mkdirs();
                        }
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                        String dateString = sdf.format(date);
                        String newfileName =  userId.toString() + "_" + user.getUsername() + "_" + dateString + "_" +originalFilename;
                        //保存文件
                        String fileContextPath = contextPath + newfileName;
                        File dest = new File(path,newfileName);

                        /**
                         * 用ftp将图片上传至Nginx所在服务器的静态目录,此处直接写死为本地路径，用作模拟
                         */
                        File nginxFile = new File("/usr/local/nginx/html"+fileContextPath);
                        if(!nginxFile.exists()){
                            multipartFile.transferTo(nginxFile);
                            nginxFile.setReadable(true, false);
                        }
                        if (!(dest.exists())) {
                            try{
                                //此处若文件已存在,则会抛出IllegalStateException
                                multipartFile.transferTo(dest);
                            }catch (IllegalStateException i){
                                System.out.println("IllegalStateException:文件已存在:"+dest.getName());
                            }
                        }
                        //MultipartFile也提供了其他一些方法, 用来获取文件的部分属性
                        //获取文件contentType
                        String contentType = multipartFile.getContentType();
                        resultInfo += (";contentType:" + contentType);
                        /*
                         * 获取name
                         * 其实这个name跟上面提到的getFileName值是一样的,
                         * 就是Map中Key的值. 即前台页面<input>中name=""
                         * 属性. 但是上面的getFileName只是得到这个Map的Key,
                         * 而Spring在处理上传文件的时候会把这个值以name属性
                         * 记录到对应的每一个文件. 如果需要从文件层面获取这个
                         * 值, 则可以使用该方法
                         */
                        String name = multipartFile.getName();
                        resultInfo += ("文件名:" + name + ";");
                        //multipartFile.getSize()获取文件大小, 单位为字节,用工具类getPrintSize()处理后转化为B、MB、KB、GB
                        String size = InfoCountUtils.getPrintSize(multipartFile.getSize());
                        resultInfo += ("文件大小:" + size);
                        if (multipartFile.getSize() > 0) {
                            //将文件路径存入数据库
                            picture.setDetails(description);
                            picture.setFilepath(fileContextPath);
                            if(!saveUserImage(picture))
                                resultInfo += "图片已存服务器，存DB失败;";
                            else
                                resultInfo += "图片已存服务器，存DB成功;";
                        }
                    }
                }
            } catch (IllegalStateException f) {
                resultInfo += "IllegalStateException:上传状态错误，可能是从非法页面提交上传";
                f.printStackTrace();
            } catch (UnauthenticatedException g) {
                resultInfo += "UnauthenticatedException:用户无上传图片权限";
                g.printStackTrace();
            } catch (Exception e) {
                resultInfo += "Exception:e";
                e.printStackTrace();
            } finally {
                return resultInfo;
            }
        }

    }

    /**
     *@Author Lyon[flowingsun007@163.com]
     *@Date 18/07/15 22:55
     *@Param [pictureQuery]
     *@Return com.flowingsun.behavior.vo.PictureQuery
     *@Description getUserImages
     * 查询用户Timeline上传的图片,默认查询时间为近一年的,根据pictureQuery来查询
     * (默认查询从第一页开始，每页20条数据)
     */
    @Override
    public PictureQuery getUserImages (PictureQuery pictureQuery){
        Long userId = (Long)SecurityUtils.getSubject().getSession().getAttribute("userId");
        if (userId != null) {
            User user = redisDAO.getRedisUser(userId);
            //缓存未命中，从数据库读user}
            if(user==null){user = userMapper.selectByPrimaryKey(userId);}
            pictureQuery.setUserid(user.getId());
            pictureQuery.setUsername(user.getUsername());
            //设置照片查询时间段,默认为最近一年
            Calendar toDate = Calendar.getInstance();
            Calendar fromDate = Calendar.getInstance();
            fromDate.add(Calendar.DAY_OF_MONTH,-365);
            pictureQuery.setStartDate(fromDate.getTime());
            pictureQuery.setEndDate(toDate.getTime());
            //查询相应时间段内照片总数
            Integer total = pictureMapper.selectCountByQueryDateRange(pictureQuery);
            if(total!=null&&total>0){
                pictureQuery.setTotal(total);
                List<PictureQuery> pictureQueryList = pictureMapper.selectByQueryDateRange(pictureQuery);
                pictureQuery.setDataList(pictureQueryList);
            }
        }
        return pictureQuery;
    }

    /**
     *@Author Lyon[flowingsun007@163.com]
     *@Date 18/07/15 21:31
     *@Param [article, userId]
     *@Return com.flowingsun.article.entity.Article
     *@Description getUserArticleBehavior
     * 用于判断给定的用户id在某篇文章下的用户行为,譬如点赞、评论等
     */
    @Override
    public Article getUserArticleBehavior(Article article, Long userId) {
        BehaviorStatus behaviorBean = new BehaviorStatus();
        Integer articleId = article.getId();
        byte flag=1;
        if(thankMapper.selectThankStatusByAidUid(userId,articleId)>=1){
            behaviorBean.setThankStatus(flag);
        }
        if(commentMapper.selectCommentStatusByAidUid(userId,articleId)>=1){
            behaviorBean.setCommentStatus(flag);
        }
        article.setBehaviorStatus(behaviorBean);
        //List<Comment> cmtList = article.getArticleCommentList();
        return article;
    }

    /**
     *@Author Lyon[flowingsun007@163.com]
     *@Date 18/07/15 21:32
     *@Param [categoryArticleQuery, userId]
     *@Return com.flowingsun.article.vo.CategoryArticleQuery
     *@Description getUserCategoryArticleBehavior
     * 此方法用于加载分类浏览文章中特定用户id下的浏览点赞评论等行为信息,具体通过遍历每篇文章调用getUserArticleBehavior()查询。
     */
    @Override
    public CategoryArticleQuery getUserCategoryArticleBehavior(CategoryArticleQuery categoryArticleQuery,  Long userId) {
        List<Article> articleList = (List<Article>) categoryArticleQuery.getDataList();
        List<Article> articles = new ArrayList<>();
        for(int i=0; i<articleList.size(); i++){
            Article item = articleList.get(i);
            Article article = getUserArticleBehavior(item,userId);
            articles.add(article);
        }
        categoryArticleQuery.setDataList(articles);
        return categoryArticleQuery;
    }


}
