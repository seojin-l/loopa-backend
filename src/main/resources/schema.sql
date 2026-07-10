-- =========================================================
-- Loopa Schema (MySQL 8)
-- Reference DDL — JPA ddl-auto=update 사용 시 참고용
-- =========================================================

CREATE TABLE IF NOT EXISTS users (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '회원 ID',
    email               VARCHAR(255) NOT NULL COMMENT '이메일(로그인 ID)',
    password            VARCHAR(255) NOT NULL COMMENT '비밀번호(해시)',
    gender              VARCHAR(10)  NOT NULL COMMENT '성별 MALE/FEMALE',
    age                 INT          NOT NULL COMMENT '나이',
    job                 VARCHAR(20)  NOT NULL COMMENT '직업(11종 enum)',
    token_balance       INT          NOT NULL DEFAULT 0 COMMENT '보유 토큰(원장 캐시)',
    created_at          DATETIME     NOT NULL COMMENT '생성일시',
    updated_at          DATETIME     NOT NULL COMMENT '수정일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) COMMENT='회원';

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '토큰 ID',
    user_id     BIGINT       NOT NULL COMMENT '소유 회원 ID',
    token       VARCHAR(255) NOT NULL COMMENT 'refresh 토큰 해시(SHA-256)',
    expires_at  DATETIME     NOT NULL COMMENT '만료일시',
    created_at  DATETIME     NOT NULL COMMENT '생성일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token (token),
    KEY idx_refresh_tokens_user (user_id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) COMMENT='리프레시 토큰 화이트리스트';

CREATE TABLE IF NOT EXISTS email_verifications (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '인증 ID',
    email       VARCHAR(255) NOT NULL COMMENT '이메일',
    code        VARCHAR(10)  NOT NULL COMMENT '인증번호',
    purpose     VARCHAR(20)  NOT NULL COMMENT '목적 SIGNUP/PASSWORD_RESET',
    verified    BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '인증 완료 여부',
    attempt_count INT        NOT NULL DEFAULT 0 COMMENT '인증 시도 횟수(최대 5)',
    expires_at  DATETIME     NOT NULL COMMENT '만료일시',
    created_at  DATETIME     NOT NULL COMMENT '생성일시',
    PRIMARY KEY (id),
    KEY idx_email_verifications_email (email)
) COMMENT='이메일 인증';

CREATE TABLE IF NOT EXISTS surveys (
    id                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '설문 ID',
    creator_id          BIGINT        NOT NULL COMMENT '작성자 ID',
    title               VARCHAR(100)  NOT NULL COMMENT '설문 제목',
    description         VARCHAR(1000) NULL     COMMENT '설문 소개',
    target              VARCHAR(50)   NULL     COMMENT '희망 설문 대상',
    category            VARCHAR(20)   NOT NULL COMMENT '카테고리(9종 enum)',
    estimated_minutes   INT           NULL     COMMENT '예상 소요 시간(분)',
    start_date          DATE          NOT NULL COMMENT '설문 시작일',
    end_date            DATE          NOT NULL COMMENT '마감일',
    shared_to_archive   BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '아카이브 공유 여부',
    archived_at         DATETIME      NULL     COMMENT '아카이브 공유일시',
    is_deleted          BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '삭제 여부(소프트 삭제)',
    created_at          DATETIME      NOT NULL COMMENT '게시일시',
    PRIMARY KEY (id),
    KEY idx_surveys_creator (creator_id),
    CONSTRAINT fk_surveys_creator FOREIGN KEY (creator_id) REFERENCES users (id)
) COMMENT='설문';

CREATE TABLE IF NOT EXISTS questions (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '문항 ID',
    survey_id       BIGINT       NOT NULL COMMENT '설문 ID',
    question_order  INT          NOT NULL COMMENT '문항 순서',
    type            VARCHAR(20)  NOT NULL COMMENT '유형 MULTIPLE_CHOICE/SUBJECTIVE',
    content         VARCHAR(200) NOT NULL COMMENT '질문 내용',
    is_required     BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '필수 응답 여부',
    allow_multiple  BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '복수 선택 허용(객관식만)',
    created_at      DATETIME     NOT NULL COMMENT '생성일시',
    PRIMARY KEY (id),
    KEY idx_questions_survey (survey_id),
    CONSTRAINT fk_questions_survey FOREIGN KEY (survey_id) REFERENCES surveys (id)
) COMMENT='문항';

CREATE TABLE IF NOT EXISTS question_options (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '보기 ID',
    question_id   BIGINT       NOT NULL COMMENT '문항 ID',
    option_order  INT          NOT NULL COMMENT '보기 순서',
    content       VARCHAR(200) NOT NULL COMMENT '보기 내용',
    created_at    DATETIME     NOT NULL COMMENT '생성일시',
    PRIMARY KEY (id),
    KEY idx_question_options_question (question_id),
    CONSTRAINT fk_question_options_question FOREIGN KEY (question_id) REFERENCES questions (id)
) COMMENT='보기(객관식 선택지)';

CREATE TABLE IF NOT EXISTS survey_responses (
    id             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '응답 ID',
    survey_id      BIGINT   NOT NULL COMMENT '설문 ID',
    respondent_id  BIGINT   NULL     COMMENT '응답자 ID(회원, NULL=게스트)',
    guest_key      VARCHAR(64) NULL  COMMENT '게스트 세션 키(회원은 NULL)',
    earned_token   INT      NOT NULL DEFAULT 0 COMMENT '획득 토큰(게스트=0)',
    submitted_at   DATETIME NOT NULL COMMENT '제출일시',
    created_at     DATETIME NOT NULL COMMENT '생성일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_response_member (survey_id, respondent_id),
    UNIQUE KEY uk_response_guest  (survey_id, guest_key),
    KEY idx_survey_responses_survey (survey_id),
    CONSTRAINT fk_survey_responses_survey FOREIGN KEY (survey_id) REFERENCES surveys (id),
    CONSTRAINT fk_survey_responses_user   FOREIGN KEY (respondent_id) REFERENCES users (id)
) COMMENT='설문 응답';

CREATE TABLE IF NOT EXISTS answers (
    id           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '답변 ID',
    response_id  BIGINT   NOT NULL COMMENT '응답 ID',
    question_id  BIGINT   NOT NULL COMMENT '문항 ID',
    answer_text  TEXT     NULL     COMMENT '주관식 답변(객관식은 NULL)',
    created_at   DATETIME NOT NULL COMMENT '생성일시',
    PRIMARY KEY (id),
    KEY idx_answers_response (response_id),
    KEY idx_answers_question (question_id),
    CONSTRAINT fk_answers_response FOREIGN KEY (response_id) REFERENCES survey_responses (id),
    CONSTRAINT fk_answers_question FOREIGN KEY (question_id) REFERENCES questions (id)
) COMMENT='문항 답변';

CREATE TABLE IF NOT EXISTS answer_options (
    id         BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    answer_id  BIGINT NOT NULL COMMENT '답변 ID',
    option_id  BIGINT NOT NULL COMMENT '선택한 보기 ID',
    PRIMARY KEY (id),
    KEY idx_answer_options_answer (answer_id),
    CONSTRAINT fk_answer_options_answer FOREIGN KEY (answer_id) REFERENCES answers (id),
    CONSTRAINT fk_answer_options_option FOREIGN KEY (option_id) REFERENCES question_options (id)
) COMMENT='답변 선택보기';

CREATE TABLE IF NOT EXISTS token_transactions (
    id                   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '거래 ID',
    user_id              BIGINT      NOT NULL COMMENT '회원 ID',
    type                 VARCHAR(30) NOT NULL COMMENT '거래 유형(6종)',
    amount               INT         NOT NULL COMMENT '변동량(부호 있음)',
    balance_after        INT         NOT NULL COMMENT '거래 후 잔액',
    related_survey_id    BIGINT      NULL     COMMENT '관련 설문 ID',
    related_response_id  BIGINT      NULL     COMMENT '관련 응답 ID',
    created_at           DATETIME    NOT NULL COMMENT '생성일시',
    PRIMARY KEY (id),
    KEY idx_token_transactions_user (user_id),
    CONSTRAINT fk_token_tx_user     FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_token_tx_survey   FOREIGN KEY (related_survey_id) REFERENCES surveys (id),
    CONSTRAINT fk_token_tx_response FOREIGN KEY (related_response_id) REFERENCES survey_responses (id)
) COMMENT='토큰 거래내역(원장)';

CREATE TABLE IF NOT EXISTS archive_views (
    id           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '열람 ID',
    viewer_id    BIGINT   NOT NULL COMMENT '열람자 ID',
    survey_id    BIGINT   NOT NULL COMMENT '설문 ID',
    token_spent  INT      NOT NULL DEFAULT 15 COMMENT '소모 토큰',
    viewed_at    DATETIME NOT NULL COMMENT '열람일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_archive_view (viewer_id, survey_id),
    CONSTRAINT fk_archive_views_user   FOREIGN KEY (viewer_id) REFERENCES users (id),
    CONSTRAINT fk_archive_views_survey FOREIGN KEY (survey_id) REFERENCES surveys (id)
) COMMENT='아카이브 열람(유료 열람 이력)';
