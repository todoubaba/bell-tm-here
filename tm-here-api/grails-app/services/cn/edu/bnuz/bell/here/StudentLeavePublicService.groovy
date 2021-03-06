package cn.edu.bnuz.bell.here

import cn.edu.bnuz.bell.http.ForbiddenException
import cn.edu.bnuz.bell.http.NotFoundException
import cn.edu.bnuz.bell.operation.ScheduleService
import cn.edu.bnuz.bell.organization.StudentService
import cn.edu.bnuz.bell.security.SecurityService
import cn.edu.bnuz.bell.service.DataAccessService
import grails.gorm.transactions.Transactional

@Transactional(readOnly = true)
class StudentLeavePublicService {
    StudentLeaveFormService studentLeaveFormService
    ScheduleService scheduleService
    DataAccessService dataAccessService
    SecurityService securityService
    StudentService studentService

    def getFormForShow(String userId, Long id) {
        def form = studentLeaveFormService.getFormInfo(id)

        if (!form) {
            throw new NotFoundException()
        }

        if (!canView(userId, form)) {
            throw new ForbiddenException()
        }

        def schedules = scheduleService.getStudentSchedules(form.term, form.studentId)

        return [
                schedules: schedules,
                form     : form,
        ]
    }

    private canView(String userId, Map form) {
        String studentId = form.studentId
        if (userId == studentId) {
            return true
        }

        if (securityService.hasRole('ROLE_ROLLCALL_DEPT_ADMIN')) {
            if (securityService.departmentId == studentService.getDepartment(studentId).id) {
                return true
            }
        }

        if (securityService.hasRole('ROLE_STUDENT_COUNSELLOR')) {
            if (userId == studentService.getCounsellor(studentId)?.id) {
                return true
            }
        }

        if (securityService.hasRole('ROLE_CLASS_SUPERVISOR')) {
            if (userId == studentService.getSupervisor(studentId)?.id) {
                return true
            }
        }

        if (securityService.hasRole('ROLE_TASK_SCHEDULE_TEACHER')) {
            if (dataAccessService.getInteger('''
    select count(distinct form.id)
    from StudentLeaveForm form
    join form.items item,
         CourseClass courseClass
    join courseClass.tasks task
    join task.schedules taskSchedule
    join task.students taskStudent
    where form.status in ('APPROVED', 'FINISHED')
      and form.term = courseClass.term
      and (
        item.taskSchedule = taskSchedule or
        item.dayOfWeek = taskSchedule.dayOfWeek or
        item.taskSchedule is null and item.dayOfWeek is null
      )
      and form.student = taskStudent.student
      and item.week between taskSchedule.startWeek and taskSchedule.endWeek
      and (
        taskSchedule.oddEven = 0 or
        taskSchedule.oddEven = 1 and item.week % 2 = 1 or
        taskSchedule.oddEven = 2 and item.week % 2 = 0
      )
      and taskSchedule.teacher.id = :teacherId
      and form.id = :id
    ''', [teacherId: userId, id: form.id]) > 0) {
                return true
            }
        }
        return false
    }

    /**
     * 查找与排课相关的学生请假。
     * @Param week 指定周次
     * @param taskScheduleIds 排课Id列表
     * @return 请假列表
     */
    def listByWeekAndTaskSchedules(Integer week, List<UUID> taskScheduleIds) {
        StudentLeaveForm.executeQuery '''
select new map(
  form.id as id,
  form.student.id as studentId,
  form.type as type
)
from StudentLeaveForm form
join form.items item, CourseClass courseClass
join courseClass.tasks task
join task.schedules taskSchedule
join task.students taskStudent
where form.status in ('APPROVED', 'FINISHED')
  and form.term = courseClass.term
  and item.week = :week
  and (item.taskSchedule = taskSchedule
    or item.dayOfWeek = taskSchedule.dayOfWeek
    or item.taskSchedule is null and item.dayOfWeek is null)
  and form.student = taskStudent.student
  and taskSchedule.id in (:taskScheduleIds)
''', [week: week, taskScheduleIds: taskScheduleIds]
    }
}
