package com.skillrental.service;
import java.util.*;
public final class PolicyContent {
    public static final Map<String, Page> PAGES = Map.of(
            "terms", new Page("Điều khoản sử dụng", List.of(
                    "SkillShare là nền tảng kết nối người có nhu cầu thuê kỹ năng với người cung cấp dịch vụ.",
                    "Người dùng có trách nhiệm cung cấp thông tin chính xác, sử dụng tài khoản đúng mục đích và không thực hiện hành vi gian lận, quấy rối hoặc xâm phạm quyền của người khác.",
                    "Customer và Provider có trách nhiệm tuân thủ những thỏa thuận đã được xác nhận trong đơn hàng.",
                    "SkillShare có thể áp dụng các biện pháp xử lý tài khoản vi phạm theo quy trình quản trị và chính sách đã công bố."
            )),
            "privacy", new Page("Chính sách quyền riêng tư", List.of(
                    "SkillShare thu thập và xử lý các thông tin cần thiết để đăng ký tài khoản, xác thực, kết nối người dùng, thực hiện giao dịch và cung cấp hỗ trợ.",
                    "Thông tin tài khoản, tin nhắn, tài liệu và dữ liệu giao dịch chỉ được truy cập bởi người dùng hoặc nhân sự có thẩm quyền theo mục đích đã được công bố.",
                    "Người dùng có thể liên hệ bộ phận hỗ trợ để yêu cầu xem xét các vấn đề liên quan đến thông tin cá nhân."
            )),
            "provider", new Page("Quy định dành cho Provider", List.of(
                    "Provider chịu trách nhiệm về tính chính xác của thông tin kỹ năng, nội dung dịch vụ, mức giá và khả năng thực hiện công việc.",
                    "Provider phải thực hiện công việc theo những điều kiện đã được xác nhận với Customer và cung cấp kết quả bàn giao phù hợp.",
                    "Việc đăng dịch vụ không đồng nghĩa dịch vụ được tự động công khai; dịch vụ có thể phải trải qua quy trình phê duyệt."
            )),
            "booking", new Page("Quy định đặt dịch vụ và bàn giao sản phẩm", List.of(
                    "Customer cần kiểm tra nội dung dịch vụ, chi phí và điều kiện thực hiện trước khi xác nhận đặt dịch vụ.",
                    "Trong quá trình thực hiện, hai bên có thể trao đổi tài liệu, file và liên kết liên quan đến đơn hàng.",
                    "Provider thực hiện bàn giao kết quả thông qua chức năng bàn giao sản phẩm của SkillShare.",
                    "Customer có trách nhiệm kiểm tra kết quả, phản hồi hoặc yêu cầu chỉnh sửa theo phạm vi công việc và quy trình nghiệm thu đã thống nhất."
            )),
            "cancellation", new Page("Chính sách hủy đơn, hoàn tiền và khiếu nại", List.of(
                    "Yêu cầu hủy đơn, hoàn tiền hoặc khiếu nại được xem xét theo trạng thái giao dịch và các điều kiện đã được công bố tại thời điểm đặt dịch vụ.",
                    "Người dùng cần cung cấp thông tin, lý do và bằng chứng liên quan để hỗ trợ việc xử lý.",
                    "Các khoản hoàn tiền chỉ được thực hiện thông qua quy trình thanh toán và hoàn tiền mà hệ thống thực tế hỗ trợ."
            )),
            "messages", new Page("Quy định về tin nhắn và tài liệu", List.of(
                    "Người dùng không được sử dụng chức năng nhắn tin để quấy rối, lừa đảo, phát tán phần mềm độc hại hoặc chia sẻ nội dung vi phạm quyền của người khác.",
                    "Tài liệu và liên kết trao đổi phải liên quan đến công việc hoặc hoạt động hỗ trợ hợp lệ.",
                    "Hội thoại hỗ trợ có thể được nhân sự có thẩm quyền xem lại để xử lý yêu cầu và đánh giá chất lượng hỗ trợ.",
                    "Việc truy cập hội thoại riêng để giải quyết tranh chấp phải tuân theo quy trình phân quyền, ghi nhận lý do và kiểm soát truy cập."
            )),
            "payments", new Page("Chính sách thanh toán và ví", List.of(
                    "Ứng dụng ghi nhận thanh toán, số dư ví, khoản giữ theo đơn và các giao dịch theo chức năng đang được triển khai. Một số thao tác trong môi trường demo chỉ cập nhật trạng thái nội bộ và không chuyển tiền ngân hàng thật.",
                    "Nghiệm thu có thể yêu cầu OTP; Provider không được xem là đã nhận quyết toán chỉ vì đã bàn giao file.",
                    "Không đưa ra cam kết thanh toán, rút tiền hoặc hoàn tiền ngoài trạng thái và quy trình thực tế của tài khoản."
            )),
            "cookies", new Page("Chính sách cookie", List.of(
                    "SkillShare sử dụng cookie phiên cần thiết để duy trì đăng nhập, bảo vệ các thao tác và hỗ trợ OAuth khi người dùng lựa chọn đăng nhập Google.",
                    "Ứng dụng hiện không công bố việc dùng cookie quảng cáo hoặc theo dõi bên thứ ba trong các chức năng được mô tả ở đây. Nếu bổ sung công cụ phân tích hoặc quảng cáo, nội dung và lựa chọn cookie cần được cập nhật."
            ))
    );

 public record Page(String title,List<String> paragraphs){}
}
